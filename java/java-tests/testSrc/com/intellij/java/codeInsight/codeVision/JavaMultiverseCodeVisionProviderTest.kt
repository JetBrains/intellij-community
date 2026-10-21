// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.daemon.impl.JavaInheritorsCodeVisionProvider
import com.intellij.codeInsight.daemon.impl.JavaReferencesCodeVisionProvider
import com.intellij.codeInsight.hints.codeVision.ModificationStampUtil
import com.intellij.codeInsight.hints.codeVision.RenameAwareReferencesCodeVisionProvider
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.codeInsight.multiverse.SingleEditorContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeVisionFixture
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.usageView.UsageViewUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.awt.event.MouseEvent

@TestApplication
internal class JavaMultiverseCodeVisionProviderTest {

  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()
    private val moduleAFixture : TestFixture<Module> = projectFixture.moduleFixture("moduleA")
    private val moduleBFixture : TestFixture<Module> = projectFixture.moduleFixture("moduleB")
    private val sharedSourceRootFixture = sharedSourceRootFixture(moduleAFixture, moduleBFixture)
  }

  private val sourceRootFixtureA = moduleAFixture.sourceRootFixture()
  private val sourceRootFixtureB = moduleBFixture.sourceRootFixture()

  private val sharedFileFixture = sharedSourceRootFixture.psiFileFixture("Shared.java", "")
  private val editorFixture = sharedFileFixture.editorFixture()
  private val codeVisionFixture = codeVisionFixture(editorFixture, sharedFileFixture)

  @Test
  fun testClassUsages() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A {
          void foo() {
            new Shared();
          }
          void bar() {
            new Shared();
          }
        }
          """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B {
          void foo() {
            new Shared();
          }
        }
          """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        public class Shared {}/*<# [3 usages] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
      package com.company;
      public class Shared {}/*<# [2 usages] #>*/
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  }

  @Test
  fun testMethodUsages() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A {
          void foo() {
            var sh = new Shared();
            sh.doShared()
          }
          void bar() {
            new Shared();
          }
        }
          """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B {
          void foo() {
            new Shared();
          }
        }
          """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
      package com.company;
      public class Shared {/*<# [3 usages] #>*/
        public void doShared() {}/*<# [1 usage] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public class Shared {/*<# [2 usages] #>*/
          public void doShared() {}/*<# [no usages] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)
  }

  @Test
  fun testClassInheritors() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A extends Shared {}
        public class A1 extends Shared {}
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B extends Shared {}
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        public class Shared{}/*<# [3 usages   2 inheritors] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public class Shared{}/*<# [2 usages   1 inheritor] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)
  }

  @Test
  fun testInterfaceInheritors() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A implements Shared {}
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B implements Shared {}
        public class C implements Shared {}
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        public interface Shared{}/*<# [2 usages   1 implementation] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public interface Shared{}/*<# [3 usages   2 implementations] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )
  }

  @Test
  fun testMethodOverrides() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        class A extends Shared {
          void foo(){}
        }
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        class B extends Shared {
          void foo(){}
        }
        class C extends Shared {
          void foo(){}
        }  
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        public class Shared{/*<# [2 usages   1 inheritor] #>*/
          public void foo(){}/*<# [no usages   1 override] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public class Shared{/*<# [3 usages   2 inheritors] #>*/
          public void foo(){}/*<# [no usages   2 overrides] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )
  }

  @Test
  fun testAbstractMethodImplementations() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        class A implements Shared {
          public void foo(){}
        }
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        class B implements Shared {
          public void foo(){}
        }
        class C implements Shared {
          public void foo(){}
        }
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        public interface Shared {/*<# [2 usages   1 implementation] #>*/
        void foo();/*<# [no usages   1 implementation] #>*/
      }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public interface Shared {/*<# [3 usages   2 implementations] #>*/
        void foo();/*<# [no usages   2 implementations] #>*/
      }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )
  }

  @Test
  fun testEnumMembers() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.E;
        class A {
          E foo() {
            bar(E.E1, E.E2, E.E3, E.E4);
          }
          void bar(E... e) {} 
        }
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.E;
        class B {
          E foo() {
            bar(E.E1, E.E2);
          }
          void bar(E... e) {} 
        }  
      """.trimIndent())

    val moduleAContext = setContext(editor, moduleAFixture.get())
    codeVision.testProviders(moduleAContext, """
        package com.company;
        public enum E {/*<# [7 usages] #>*/
          E1, E2, E3, E4/*<# [1 usage] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public enum E {/*<# [5 usages] #>*/
          E1, E2, E3, E4/*<# [1 usage] #>*/
        }
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId,
                             JavaInheritorsCodeVisionProvider().groupId
    )

  }

  @Test
  fun testHostLensesAfterSharedRootUnlinked() = timeoutRunBlocking {
    val codeVision = codeVisionFixture.get()
    val editor = editorFixture.get()

    addFileToSourceRoot(sourceRootFixtureA.get(), "A.java", """
        import com.company.Shared;
        public class A extends Shared {}
        public class A1 extends Shared {}
      """.trimIndent())

    addFileToSourceRoot(sourceRootFixtureB.get(), "B.java", """
        import com.company.Shared;
        public class B extends Shared {}
      """.trimIndent())

    val moduleBContext = setContext(editor, moduleBFixture.get())
    codeVision.testProviders(moduleBContext, """
        package com.company;
        public class Shared{}/*<# [2 usages   1 inheritor] #>*/
      """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

    unlinkSharedRootFromModuleB()
    try {
      val moduleAContext = requireNotNull(ProjectModelContextBridge.getInstance(projectFixture.get()).getContext(moduleAFixture.get()))
      codeVision.testProviders(moduleAContext, """
          package com.company;
          public class Shared{}/*<# [3 usages   2 inheritors] #>*/
        """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)
    }
    finally {
      linkSharedRootToModuleB()
    }
  }

  @Test
  fun testStaleUsagesLensClickAfterSharedRootUnlinked() = timeoutRunBlocking {
    withSharedRootUnlinkedAfterLenses { provider, editor, lensesBeforeUnlink ->
      assertNull(click(provider, editor, lensesBeforeUnlink.last()), "the click on a stale lens must not give a PSI element of moduleB")
    }
  }

  @Test
  fun testUsagesLensesAfterSharedRootUnlinked() = timeoutRunBlocking {
    withSharedRootUnlinkedAfterLenses { provider, editor, _ ->
      val clicked = requireNotNull(click(provider, editor, computeLenses(provider, editor, null).last()))
      assertEquals("doShared()", readAction { UsageViewUtil.getLongName(clicked) })
      val moduleAContext = requireNotNull(ProjectModelContextBridge.getInstance(projectFixture.get()).getContext(moduleAFixture.get()))
      assertEquals(moduleAContext, readAction { clicked.containingFile.codeInsightContext })
    }
  }

  /**
   * Computes the lenses of the shared file in moduleB, then removes the shared root from moduleB.
   */
  private suspend fun withSharedRootUnlinkedAfterLenses(
    action: suspend (ClickRecordingReferencesCodeVisionProvider, Editor, List<ClickableTextCodeVisionEntry>) -> Unit,
  ) {
    val editor = editorFixture.get()
    setSharedText("""
      package com.company;
      public class Shared {
        public void doShared() {}
      }
    """.trimIndent())
    val moduleBContext = setContext(editor, moduleBFixture.get())
    val provider = ClickRecordingReferencesCodeVisionProvider()
    val disposable = Disposer.newDisposable()
    try {
      TestModeFlags.set(CodeVisionHost.isCodeVisionTestKey, true, disposable)
      projectFixture.get().waitForSmartMode()
      val lensesBeforeUnlink = computeLenses(provider, editor, moduleBContext)
      assertEquals(listOf("Shared", "doShared"), lensesBeforeUnlink.map { it.text })

      unlinkSharedRootFromModuleB()
      try {
        action(provider, editor, lensesBeforeUnlink)
      }
      finally {
        linkSharedRootToModuleB()
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private suspend fun computeLenses(
    provider: ClickRecordingReferencesCodeVisionProvider,
    editor: Editor,
    context: CodeInsightContext?,
  ): List<ClickableTextCodeVisionEntry> {
    if (context != null) {
      readAction {
        val file = requireNotNull(PsiManager.getInstance(projectFixture.get()).findFile(sharedFileFixture.get().virtualFile, context))
        ModificationStampUtil.putCurrentModificationStamp(editor, file)
      }
    }
    val state = withContext(Dispatchers.Default) { provider.computeCodeVision(editor, null) }
    return assertInstanceOf(CodeVisionState.Ready::class.java, state).result.map { it.second as ClickableTextCodeVisionEntry }
  }

  private suspend fun click(
    provider: ClickRecordingReferencesCodeVisionProvider,
    editor: Editor,
    lens: ClickableTextCodeVisionEntry,
  ): PsiElement? {
    provider.clickedElement = null
    withContext(Dispatchers.EDT) {
      lens.onClick(null, editor)
    }
    return provider.clickedElement
  }

  private suspend fun unlinkSharedRootFromModuleB() {
    val root = sharedFileFixture.get().virtualFile.parent
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(moduleBFixture.get()) { model ->
        model.removeContentEntry(model.contentEntries.first { it.file == root })
      }
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(projectFixture.get())
  }

  private suspend fun linkSharedRootToModuleB() {
    val root = sharedFileFixture.get().virtualFile.parent
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(moduleBFixture.get()) { model ->
        model.addContentEntry(root).addSourceFolder(root, false)
      }
    }
    IndexingTestUtil.suspendUntilIndexesAreReady(projectFixture.get())
  }

  private suspend fun setSharedText(text: String) {
    edtWriteAction {
      sharedFileFixture.get().virtualFile.setBinaryContent(text.toByteArray())
      PsiDocumentManager.getInstance(projectFixture.get()).commitAllDocuments()
    }
  }

  private class ClickRecordingReferencesCodeVisionProvider : RenameAwareReferencesCodeVisionProvider() {
    private val delegate = JavaReferencesCodeVisionProvider()

    @Volatile
    var clickedElement: PsiElement? = null

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
      clickedElement = element
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? = (element as? PsiNamedElement)?.name

    override fun acceptsElement(element: PsiElement): Boolean = delegate.acceptsElement(element)

    override fun acceptsFile(file: PsiFile): Boolean = delegate.acceptsFile(file)

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
      get() = emptyList()

    override val id: String
      get() = "test.click.recording.references"
  }

  private suspend fun addFileToSourceRoot(sourceRoot: PsiDirectory, fileName: String, fileContent: String) {
    withContext(Dispatchers.EDT) {
      edtWriteAction {
        sourceRoot.createFile(fileName).also {
          it.virtualFile.setBinaryContent(fileContent.toByteArray())
        }
      }
    }
  }

  private fun setContext(editor: Editor, module: Module) : CodeInsightContext {
    val project = projectFixture.get()
    val context = requireNotNull(ProjectModelContextBridge.getInstance(project).getContext(module))
    EditorContextManager.getInstance(project).setEditorContext(editor, SingleEditorContext(context))
    return context
  }
}