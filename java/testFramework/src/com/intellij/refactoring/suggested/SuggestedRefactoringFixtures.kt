// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.util.ui.EDT as EdtUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [action] on the EDT. On the EDT, it runs [action] at once, so a nested call does not wait for itself.
 */
private fun <T> onEdt(action: () -> T): T {
  if (EdtUtil.isCurrentThreadEdt()) {
    return action()
  }
  return timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) {
      action()
    }
  }
}

/**
 * The fixture of the suggested refactoring tests. Create it with [suggestedRefactoringFixture].
 *
 * The editing helpers change the editor of [fixture] as a user does. Call them on the EDT,
 * for example in the editing actions of [doTest].
 * [doTest], [doTestRename] and [doTestChangeSignature] run on the EDT from the configure call to the last check.
 */
@TestOnly
class SuggestedRefactoringFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The file type of the file that [doTest] configures.
   */
  val fileType: LanguageFileType,
) {
  val project: Project get() = fixture.project
  val editor: Editor get() = fixture.editor
  val file: PsiFile get() = fixture.file

  /**
   * When it is `true`, [doTest] does not check the highlighting of the initial text.
   */
  var ignoreErrorsBefore: Boolean = false

  /**
   * When it is `true`, [doTest] does not check the highlighting after the refactoring.
   */
  var ignoreErrorsAfter: Boolean = false

  fun deleteTextAtCaret(text: String): Unit = editAction {
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset, offset + text.length))
    require(actualText == text)
    editor.document.deleteString(offset, offset + text.length)
  }

  fun deleteTextBeforeCaret(text: String): Unit = editAction {
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset - text.length, offset))
    require(actualText == text)
    editor.document.deleteString(offset - text.length, offset)
  }

  fun replaceTextAtCaret(oldText: String, newText: String): Unit = editAction {
    val offset = editor.caretModel.offset
    val actualText = editor.document.getText(TextRange(offset, offset + oldText.length))
    require(actualText == oldText)
    editor.document.replaceString(offset, offset + oldText.length, newText)
  }

  fun type(text: String) {
    fixture.type(text)
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
  }

  fun performAction(actionId: String) {
    fixture.performEditorAction(actionId)
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
  }

  /**
   * Runs [action] in a command and a write action, then commits the documents.
   */
  fun editAction(action: () -> Unit) {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    executeCommand {
      runWriteAction {
        action()
        psiDocumentManager.commitAllDocuments()
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(editor.document)
      }
    }
  }

  /**
   * Runs [editingActions], commits the documents, and waits for the signature analysis.
   */
  fun executeEditingActions(editingActions: SuggestedRefactoringFixture.() -> Unit) {
    editingActions()
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    waitCoroutinesBlocking(SuggestedRefactoringChangeListener.getSignatureAnalysisScope(project), 5000)
  }

  fun suggestedRefactoringIntention(): IntentionAction? {
    return fixture.availableIntentions.firstOrNull { it.familyName == "Suggested Refactoring" }
  }

  /**
   * Checks that the change signature refactoring after [editingActions] gives [expectedTextAfter].
   * When [expectedPresentation] is not `null`, it also checks the dump of the signature change presentation.
   */
  fun doTestChangeSignature(
    initialText: String,
    expectedTextAfter: String,
    usagesName: String,
    expectedPresentation: String? = null,
    editingActions: SuggestedRefactoringFixture.() -> Unit,
  ) {
    doTest(
      initialText,
      RefactoringBundle.message("suggested.refactoring.change.signature.intention.text", usagesName),
      expectedTextAfter,
      checkPresentation = {
        if (expectedPresentation != null) {
          val state = SuggestedRefactoringProviderImpl.getInstance(project).state!!
            .let { it.refactoringSupport.availability.refineSignaturesWithResolve(it) }
          assertEquals(SuggestedRefactoringState.ErrorLevel.NO_ERRORS, state.errorLevel)
          assertNotEquals(state.oldSignature, state.newSignature)
          val refactoringSupport = state.refactoringSupport
          val data = refactoringSupport.availability.detectAvailableRefactoring(state) as SuggestedChangeSignatureData
          val model = refactoringSupport.ui.buildSignatureChangePresentation(data.oldSignature, data.newSignature)
          assertEquals(expectedPresentation, model.dump().trim())
        }
      },
      editingActions
    )
  }

  /**
   * Checks that the rename refactoring of [oldName] after [editingActions] gives [textAfterRefactoring].
   * [newName] is not checked. The intention text names only [oldName].
   */
  @Suppress("unused")
  fun doTestRename(
    initialText: String,
    textAfterRefactoring: String,
    oldName: String,
    newName: String,
    editingActions: SuggestedRefactoringFixture.() -> Unit,
  ) {
    doTest(
      initialText,
      RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName),
      textAfterRefactoring,
      {},
      editingActions
    )
  }

  /**
   * Configures [initialText], runs [editingActions], and invokes the suggested refactoring with the name [actionName].
   * Then it checks the text and the caret position of [textAfterRefactoring].
   */
  fun doTest(
    initialText: String,
    actionName: String,
    textAfterRefactoring: String,
    checkPresentation: SuggestedRefactoringFixture.() -> Unit,
    editingActions: SuggestedRefactoringFixture.() -> Unit,
  ): Unit = onEdt {
    fixture.configureByText(fileType, initialText)

    if (!ignoreErrorsBefore) {
      fixture.testHighlighting(false, false, false, fixture.file.virtualFile)
    }
    else {
      // Invoking highlighting has side effects (in our particular case, we are interested in initializing
      // `SuggestedRefactoringChangeListener`). That's why we have to invoke highlighting even when `ignoreErrorsBefore` is `true`
      fixture.doHighlighting()
    }

    executeEditingActions(editingActions)

    val intention = suggestedRefactoringIntention()
    assertNotNull(intention, "No refactoring available")

    assertEquals(actionName, intention!!.text, "Action name")

    checkPresentation()

    executeCommand(project) {
      intention.invoke(project, editor, file)

      runWriteAction {
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      }
    }

    val index = textAfterRefactoring.indexOf("<caret>")
    if (index >= 0) {
      val text = textAfterRefactoring.substring(0, index) +
                 textAfterRefactoring.substring(index + "<caret>".length)
      assertEquals(text, editor.document.text)

      assertEquals(index, editor.caretModel.offset, "Caret position")
    }
    else {
      assertEquals(textAfterRefactoring, editor.document.text)
    }

    if (!ignoreErrorsAfter) {
      fixture.testHighlighting(false, false, false, fixture.file.virtualFile)
    }
  }
}

/**
 * Creates a [SuggestedRefactoringFixture] for one test. Declare it as an instance field.
 * It is the JUnit 5 form of [BaseSuggestedRefactoringTest] and [LightJavaCodeInsightFixtureTestCaseWithUtils].
 */
@TestOnly
@JvmOverloads
fun suggestedRefactoringFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileType: LanguageFileType = JavaFileType.INSTANCE,
): TestFixture<SuggestedRefactoringFixture> = testFixture("suggestedRefactoringFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  initialized(SuggestedRefactoringFixture(fixture, fileType)) {}
}

/**
 * The fixture of the tests of [SuggestedRefactoringChangeCollector]. Create it with [suggestedRefactoringChangeCollectorFixture].
 */
@TestOnly
class SuggestedRefactoringChangeCollectorFixture<TDeclaration : PsiElement> internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val language: Language,
  private val fileType: FileType,
  private val addDeclaration: (PsiFile, String) -> TDeclaration,
  private val presentation: (Signature, (Any) -> String?) -> String,
  private val collector: SuggestedRefactoringChangeCollector,
) {
  val project: Project get() = fixture.project
  val editor: Editor get() = fixture.editor
  val file: PsiFile get() = fixture.file

  /**
   * Adds the declaration [initialDeclarationText] to an empty file, runs [editingActions] on it,
   * and checks the old and the new signature of the collector. The test runs on the EDT.
   */
  fun doTest(
    initialDeclarationText: String,
    vararg editingActions: SuggestedRefactoringChangeCollectorFixture<TDeclaration>.(TDeclaration) -> Unit,
    wrapIntoCommandAndWriteAction: Boolean = true,
    expectedOldSignature: String?,
    expectedNewSignature: String?,
  ): Unit = onEdt {
    fixture.configureByText(fileType, "")

    val refactoringSupport = SuggestedRefactoringSupport.forLanguage(language)!!
    var declaration: TDeclaration? = null
    executeCommand {
      runWriteAction {
        declaration = addDeclaration(file, initialDeclarationText)
        collector.editingStarted(declaration!!, refactoringSupport)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
      }
    }

    for (action in editingActions) {
      if (wrapIntoCommandAndWriteAction) {
        executeCommand {
          runWriteAction {
            action(declaration!!)
          }
        }
      }
      else {
        action(declaration!!)
      }
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      require(declaration!!.isValid)
      collector.nextSignature(declaration!!, refactoringSupport)
    }

    checkState(expectedOldSignature, expectedNewSignature)
  }

  private fun checkState(expectedOldSignature: String?, expectedNewSignature: String?) {
    val oldSignature = collector.state?.oldSignature
    val newSignature = collector.state?.newSignature
    assertEquals(
      expectedOldSignature,
      oldSignature?.let { presentation(it) { null } },
      "Old signature"
    )
    assertEquals(
      expectedNewSignature,
      newSignature?.let {
        presentation(it) { id ->
          val oldParameter = oldSignature!!.parameterById(id)
          if (oldParameter != null) {
            "initialIndex = " + oldSignature.parameterIndex(oldParameter)
          }
          else {
            "new"
          }
        }
      },
      "New signature"
    )
  }
}

/**
 * Creates a [SuggestedRefactoringChangeCollectorFixture] for one test. Declare it as an instance field.
 * It is the JUnit 5 form of [BaseSuggestedRefactoringChangeCollectorTest].
 *
 * [addDeclaration] adds the declaration text to the file in a write action and returns the declaration.
 * [presentation] gives the text of a signature. Its second parameter gives the label of a parameter by its id.
 */
@TestOnly
fun <TDeclaration : PsiElement> suggestedRefactoringChangeCollectorFixture(
  projectFixture: TestFixture<LightJavaProject>,
  language: Language,
  fileType: FileType,
  addDeclaration: (PsiFile, String) -> TDeclaration,
  presentation: (Signature, (Any) -> String?) -> String,
): TestFixture<SuggestedRefactoringChangeCollectorFixture<TDeclaration>> = testFixture("suggestedRefactoringChangeCollectorFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  val collector = SuggestedRefactoringChangeCollector(SuggestedRefactoringAvailabilityIndicator(fixture.project))
  initialized(SuggestedRefactoringChangeCollectorFixture(fixture, language, fileType, addDeclaration, presentation, collector)) {
    withContext(Dispatchers.EDT) {
      collector.reset()
    }
  }
}

/**
 * The fixture of the tests of [SuggestedRefactoringChangeListener]. Create it with [suggestedRefactoringChangeListenerFixture].
 *
 * [setup] and [perform] run on the EDT. A [perform] in the action of another [perform] runs at once.
 */
@TestOnly
class SuggestedRefactoringChangeListenerFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val fileType: FileType,
) {
  private val watcher = Watcher()

  init {
    SuggestedRefactoringChangeListener(fixture.project, watcher, fixture.testRootDisposable)
  }

  val project: Project get() = fixture.project
  val editor: Editor get() = fixture.editor
  val file: PsiFile get() = fixture.file

  /**
   * Configures a file of the file type of the fixture with [fileText].
   */
  fun setup(fileText: String): Unit = onEdt {
    fixture.configureByText(fileType, fileText)
  }

  /**
   * Runs [action], waits for the signature analysis, and checks that the listener reported [expectedLog].
   */
  fun perform(vararg expectedLog: String, action: SuggestedRefactoringChangeListenerFixture.() -> Unit): Unit = onEdt {
    watcher.check("")
    action()
    executeCommand {
      runWriteAction {
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
      }
    }
    waitForSignatureAnalysis()
    watcher.check(expectedLog.joinToString(separator = "\n"))
  }

  private fun waitForSignatureAnalysis() {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    waitCoroutinesBlocking(SuggestedRefactoringChangeListener.getSignatureAnalysisScope(project), 5000)
  }

  /**
   * Inserts [text] at [offset] of the document in a command and a write action. Call it on the EDT.
   */
  fun insertString(offset: Int, text: String) {
    executeCommand {
      runWriteAction {
        editor.document.insertString(offset, text)
      }
    }
  }

  private class Watcher : SuggestedRefactoringSignatureWatcher {
    private val log = StringBuilder()

    fun check(expectedLog: String) {
      assertEquals(expectedLog, log.toString().trim())
      log.clear()
    }

    private fun StringBuilder.appendSignature(declaration: PsiElement) {
      val refactoringSupport = SuggestedRefactoringSupport.forLanguage(declaration.language)!!
      val range = refactoringSupport.signatureRange(declaration)!!
      append("'")
      val text = declaration.containingFile.text
        .substring(range.startOffset, range.endOffset)
        .replace("\n", "\\n")
      append(text)
      append("'")
    }

    override fun editingStarted(declaration: PsiElement, refactoringSupport: SuggestedRefactoringSupport) {
      log.append("editingStarted: ")
      log.appendSignature(declaration)
      log.append("\n")
    }

    override fun nextSignature(anchor: PsiElement, refactoringSupport: SuggestedRefactoringSupport) {
      if (refactoringSupport.hasSyntaxError(anchor)) return

      log.append("nextSignature: ")
      log.appendSignature(anchor)
      log.append("\n")
    }

    override fun inconsistentState() {
      log.append("inconsistentState\n")
    }

    override fun reset() {
      log.append("reset\n")
    }
  }
}

/**
 * Creates a [SuggestedRefactoringChangeListenerFixture] for one test. Declare it as an instance field.
 * It is the JUnit 5 form of [BaseSuggestedRefactoringChangeListenerTest].
 */
@TestOnly
fun suggestedRefactoringChangeListenerFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileType: FileType,
): TestFixture<SuggestedRefactoringChangeListenerFixture> = testFixture("suggestedRefactoringChangeListenerFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  val listenerFixture = withContext(Dispatchers.EDT) {
    SuggestedRefactoringChangeListenerFixture(fixture, fileType)
  }
  initialized(listenerFixture) {}
}
