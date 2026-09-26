// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.multiverse

import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.project.stateStore
import com.intellij.psi.impl.file.impl.moduleContext
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.ui.popup.PopupFactoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SWITCHER_PROVIDER_CLASS = "com.intellij.openapi.editor.impl.multiverse.CodeInsightContextSwitcherProvider"

/**
 * Tests for the code insight context switcher combobox.
 * The tests obtain the action through the public [InspectionWidgetActionProvider] extension point
 * and assert on the presentation that [AnAction.update] produces.
 */
@TestApplication
internal class CodeInsightContextSwitcherTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()
    private val moduleAFixture: TestFixture<Module> = projectFixture.moduleFixture("moduleA")
    private val moduleBFixture: TestFixture<Module> = projectFixture.moduleFixture("moduleB")
    private val sharedRootFixture = sharedSourceRootFixture(moduleAFixture, moduleBFixture)
  }

  // a file with two contexts: the shared root belongs to moduleA and moduleB
  private val sharedFileFixture = sharedRootFixture.psiFileFixture("Shared.txt", "")
  private val sharedEditorFixture = sharedFileFixture.editorFixture()

  // a file with one context: the root belongs to moduleA only
  private val soloRootFixture = moduleAFixture.sourceRootFixture()
  private val soloFileFixture = soloRootFixture.psiFileFixture("Solo.txt", "")
  private val soloEditorFixture = soloFileFixture.editorFixture()

  private val project get() = projectFixture.get()

  @Test
  @Timeout(60)
  fun `switcher is visible for a file with two contexts and shows the current module`() = timeoutRunBlocking(30.seconds) {
    val editor = sharedEditorFixture.get()
    withSwitcher(editor) { action ->
      setEditorContext(editor, moduleAFixture)
      waitUntil("the switcher must become visible with text 'moduleA'") {
        val presentation = updatedPresentation(action)
        presentation.isVisible && presentation.text == "moduleA"
      }
    }
  }

  @Test
  @Timeout(60)
  fun `switcher is hidden for a file with a single context`() = timeoutRunBlocking(30.seconds) {
    val editor = soloEditorFixture.get()
    val file = soloFileFixture.get().virtualFile
    withSwitcher(editor) { action ->
      waitUntil("the file must have exactly one context") { contextCount(file) == 1 }
      assertInvisibleFor(1.seconds, action)
    }
  }

  @Test
  @Timeout(60)
  fun `switcher appears when contexts go from one to two`() = timeoutRunBlocking(30.seconds) {
    val editor = soloEditorFixture.get()
    val file = soloFileFixture.get().virtualFile
    val root = soloRootFixture.get().virtualFile
    val moduleB = moduleBFixture.get()
    withSwitcher(editor) { action ->
      waitUntil("the file must have exactly one context") { contextCount(file) == 1 }
      assertFalse(updatedPresentation(action).isVisible)

      try {
        addContentAndSourceRoot(moduleB, root)
        waitUntil("the file must get a second context") { contextCount(file) == 2 }
        waitUntil("the switcher must become visible") { updatedPresentation(action).isVisible }
      }
      finally {
        removeContentRoot(moduleB, root)
      }
    }
  }

  @Test
  @Timeout(60)
  fun `switcher disappears when contexts go from two to one`() = timeoutRunBlocking(30.seconds) {
    val editor = soloEditorFixture.get()
    val file = soloFileFixture.get().virtualFile
    val root = soloRootFixture.get().virtualFile
    val moduleB = moduleBFixture.get()
    withSwitcher(editor) { action ->
      try {
        addContentAndSourceRoot(moduleB, root)
        waitUntil("the file must get a second context") { contextCount(file) == 2 }
        waitUntil("the switcher must become visible") { updatedPresentation(action).isVisible }
      }
      finally {
        removeContentRoot(moduleB, root)
      }

      waitUntil("the file must return to one context") { contextCount(file) == 1 }
      waitUntil("the switcher must become invisible") { !updatedPresentation(action).isVisible }
    }
  }

  @Test
  @Timeout(60)
  fun `popup lists all contexts and updates when the set changes`() = timeoutRunBlocking(30.seconds) {
    val editor = sharedEditorFixture.get()
    val file = sharedFileFixture.get().virtualFile
    val root = sharedRootFixture.get().virtualFile
    withSwitcher(editor) { action ->
      setEditorContext(editor, moduleAFixture)
      waitUntil("the popup must list both modules") {
        popupItemTexts(action) == listOf("moduleA", "moduleB")
      }

      val moduleC = createModule("moduleC")
      try {
        addContentAndSourceRoot(moduleC, root)
        waitUntil("the file must get a third context") { contextCount(file) == 3 }
        waitUntil("the popup must list three modules") {
          popupItemTexts(action) == listOf("moduleA", "moduleB", "moduleC")
        }
      }
      finally {
        disposeModule(moduleC)
      }
    }
  }

  @Test
  @Timeout(60)
  fun `switcher follows an external editor context change`() = timeoutRunBlocking(30.seconds) {
    val editor = sharedEditorFixture.get()
    withSwitcher(editor) { action ->
      setEditorContext(editor, moduleAFixture)
      waitUntil("the switcher must show 'moduleA'") { updatedPresentation(action).text == "moduleA" }

      setEditorContext(editor, moduleBFixture)
      waitUntil("the switcher must show 'moduleB'") { updatedPresentation(action).text == "moduleB" }
    }
  }

  @Test
  @Timeout(60)
  fun `performing a popup item applies the context to the editor`() = timeoutRunBlocking(30.seconds) {
    val editor = sharedEditorFixture.get()
    withSwitcher(editor) { action ->
      setEditorContext(editor, moduleAFixture)
      waitUntil("the popup must list both modules") {
        popupItemTexts(action) == listOf("moduleA", "moduleB")
      }

      val itemAction = popupItemAction(action, "moduleB")
      itemAction.actionPerformed(TestActionEvent.createTestEvent(itemAction))

      waitUntil("the editor context must switch to moduleB") {
        currentEditorContextModule(editor)?.name == "moduleB"
      }
    }
  }

  @Test
  @Timeout(60)
  fun `performing a stale popup item does not change the editor context`() = timeoutRunBlocking(30.seconds) {
    val editor = sharedEditorFixture.get()
    val file = sharedFileFixture.get().virtualFile
    val root = sharedRootFixture.get().virtualFile
    withSwitcher(editor) { action ->
      setEditorContext(editor, moduleAFixture)

      val moduleC = createModule("moduleC")
      val staleItemAction: AnAction
      try {
        addContentAndSourceRoot(moduleC, root)
        waitUntil("the popup must list moduleC") { popupItemTexts(action).contains("moduleC") }
        staleItemAction = popupItemAction(action, "moduleC")
      }
      finally {
        disposeModule(moduleC)
      }
      waitUntil("the file must return to two contexts") { contextCount(file) == 2 }

      // The context invalidation drops the editor context, and the manager re-infers the preferred one.
      // The re-inferred module is moduleA or moduleB, in no guaranteed order. Assert against it, not against moduleA.
      val moduleAfterRemoval = requireNotNull(currentEditorContextModule(editor)).name

      staleItemAction.actionPerformed(TestActionEvent.createTestEvent(staleItemAction))

      assertHoldsFor(500.milliseconds, "the editor context must stay on $moduleAfterRemoval") {
        currentEditorContextModule(editor)?.name == moduleAfterRemoval
      }
    }
  }

  @Test
  @Timeout(60)
  fun `disposed switcher stops reacting to context changes`() = timeoutRunBlocking(30.seconds) {
    val editor = sharedEditorFixture.get()
    withSwitcher(editor) { action ->
      setEditorContext(editor, moduleAFixture)
      waitUntil("the switcher must show 'moduleA'") { updatedPresentation(action).text == "moduleA" }

      Disposer.dispose(action as Disposable)

      setEditorContext(editor, moduleBFixture)
      assertHoldsFor(1.seconds, "the disposed switcher must keep showing 'moduleA'") {
        updatedPresentation(action).text == "moduleA"
      }
    }
  }

  private suspend fun <T> withSwitcher(editor: Editor, block: suspend (AnAction) -> T): T {
    val group = withContext(Dispatchers.EDT) {
      val provider = InspectionWidgetActionProvider.EP_NAME.extensionList.first { it.javaClass.name == SWITCHER_PROVIDER_CLASS }
      requireNotNull(provider.createAction(editor)) { "the switcher provider must create an action for $editor" }
    }
    try {
      // The provider returns the reset button and the combo as one group. These tests drive the combo.
      val combo = (group as DefaultActionGroup).childActionsOrStubs.filterIsInstance<ExpandableComboAction>().single()
      return block(combo)
    }
    finally {
      Disposer.dispose(group as Disposable)
    }
  }

  private fun updatedPresentation(action: AnAction): Presentation {
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    return event.presentation
  }

  private suspend fun popupItemTexts(action: AnAction): List<String> = withPopupItems(action) { items -> items.map { it.text } }

  private suspend fun popupItemAction(action: AnAction, text: String): AnAction =
    withPopupItems(action) { items -> items.single { it.text == text }.action }

  private suspend fun <T> withPopupItems(action: AnAction, block: (List<PopupFactoryImpl.ActionItem>) -> T): T {
    return withContext(Dispatchers.EDT) {
      val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
      val popup = (action as ExpandableComboAction).createPopup(event) as ListPopup
      try {
        block(popup.listStep.values.filterIsInstance<PopupFactoryImpl.ActionItem>())
      }
      finally {
        Disposer.dispose(popup)
      }
    }
  }

  private suspend fun setEditorContext(editor: Editor, moduleFixture: TestFixture<Module>) {
    val context = moduleFixture.moduleContext()
    edtWriteAction {
      EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(context))
    }
  }

  private suspend fun currentEditorContextModule(editor: Editor): Module? = readAction {
    (EditorContextManager.getInstance(project).getEditorContexts(editor).mainContext as? ModuleContext)?.getModule()
  }

  private suspend fun contextCount(file: VirtualFile): Int = readAction {
    CodeInsightContextManager.getInstance(project).getCodeInsightContexts(file).size
  }

  private suspend fun addContentAndSourceRoot(module: Module, root: VirtualFile) {
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { model ->
        model.addContentEntry(root).addSourceFolder(root, false)
      }
    }
  }

  private suspend fun removeContentRoot(module: Module, root: VirtualFile) {
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { model ->
        val entry = model.contentEntries.firstOrNull { it.file == root } ?: return@updateModel
        model.removeContentEntry(entry)
      }
    }
  }

  private suspend fun createModule(name: String): Module {
    val moduleManager = ModuleManager.getInstanceAsync(project)
    val model = moduleManager.getModifiableModel()
    val module = model.newModule(project.stateStore.projectBasePath.resolve("$name.iml"), EmptyModuleType.getInstance().id)
    edtWriteAction { model.commit() }
    return module
  }

  private suspend fun disposeModule(module: Module) {
    val moduleManager = ModuleManager.getInstanceAsync(project)
    val model = moduleManager.getModifiableModel()
    model.disposeModule(module)
    edtWriteAction { model.commit() }
  }

  /**
   * Asserts that the switcher stays invisible for the whole [duration].
   * A single check is not enough because the initial `NotLoaded` state is also invisible.
   */
  private suspend fun assertInvisibleFor(duration: Duration, action: AnAction) {
    assertHoldsFor(duration, "the switcher must stay invisible") { !updatedPresentation(action).isVisible }
  }

  private suspend fun assertHoldsFor(duration: Duration, message: String, condition: suspend () -> Boolean) {
    val deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
    while (System.currentTimeMillis() < deadline) {
      assertTrue(condition(), message)
      delay(50.milliseconds)
    }
  }
}
