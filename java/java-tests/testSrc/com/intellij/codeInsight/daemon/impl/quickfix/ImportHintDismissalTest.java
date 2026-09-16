// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.hint.EditorHintListener;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;

public class ImportHintDismissalTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String UNRESOLVED_NAME = "ArrayList";

  private EditorHintFixture myHintFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // a test shows no real hint window, so the Escape action stays disabled.
    // This fixture marks each new hint as visible, which enables the Escape action.
    myHintFixture = new EditorHintFixture(getTestRootDisposable());
  }

  public void testEscapeKeyKeepsTheImportHintHidden() {
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));
    assertFalse(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));

    showImportHint(editor, reference);
    assertNotNull(myHintFixture.getCurrentHintText());
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);

    assertNull(myHintFixture.getCurrentHintText());
    assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
  }

  public void testEscapeKeyKeepsTheHintHiddenForOneReferenceOnly() {
    @Language("JAVA")
    String text = "class X { ArrayList first; ArrayList second; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement firstReference = findReference(text.indexOf(UNRESOLVED_NAME));
    PsiJavaCodeReferenceElement secondReference = findReference(text.lastIndexOf(UNRESOLVED_NAME));
    assertNotSame(firstReference, secondReference);

    showImportHint(editor, firstReference);
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);

    assertTrue(ImportHintDismissalTracker.isDismissed(editor, firstReference, UNRESOLVED_NAME));
    assertFalse(ImportHintDismissalTracker.isDismissed(editor, secondReference, UNRESOLVED_NAME));
  }

  public void testTypingInTheReferenceNameShowsTheHintAgain() {
    @Language("JAVA")
    String text = "class X { ArrayList<caret> c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(myFixture.getFile().getText().indexOf(UNRESOLVED_NAME));

    showImportHint(editor, reference);
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);
    assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));

    myFixture.type("s");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    String changedName = UNRESOLVED_NAME + "s";
    PsiJavaCodeReferenceElement changedReference = findReference(myFixture.getFile().getText().indexOf(changedName));

    assertFalse(ImportHintDismissalTracker.isDismissed(editor, changedReference, changedName));
  }

  public void testHintWhichHidesWithoutTheEscapeKeyStaysAvailable() {
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));

    showImportHint(editor, reference);
    HintManagerImpl.getInstanceImpl().hideAllHints();

    assertFalse(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
  }

  public void testClassImportHintIsNotShownAfterTheEscapeKey() throws ExecutionException, InterruptedException {
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));

    ImportClassFix fix = createImportFix(reference);
    assertEquals(ImportClassFixBase.Result.POPUP_SHOWN, fix.doFix(editor, true, true, false));

    showImportHint(editor, reference);
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);

    assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, fix.doFix(editor, true, true, false));
  }

  public void testTheClientReportOfTheEscapeKeyKeepsTheImportHintHidden() {
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));

    showImportHint(editor, reference);
    HintManagerImpl.getInstanceImpl().dismissCurrentQuestionHint(editor);

    assertNull(myHintFixture.getCurrentHintText());
    assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
  }

  public void testTheEscapeKeyKeepsTheHintHiddenAfterTheTooltipLeftTheScreen() {
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));
    Ref<LightweightHint> shownHint = new Ref<>();
    ApplicationManager.getApplication().getMessageBus().connect(getTestRootDisposable())
      .subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
        @Override
        public void hintShown(@NotNull Editor hintEditor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
          shownHint.set(hint);
        }
      });

    showImportHint(editor, reference);
    // the tooltip can leave the screen before the key press, and the hint then stays on the hint stack.
    // The Escape action hides such a hint from its own enablement check
    shownHint.get().putUserData(LightweightHint.SHOWN_AT_DEBUG, Boolean.FALSE);
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, false);

    assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
  }

  private @NotNull PsiJavaCodeReferenceElement findReference(int offset) {
    PsiReference reference = myFixture.getFile().findReferenceAt(offset);
    assertInstanceOf(reference, PsiJavaCodeReferenceElement.class);
    return (PsiJavaCodeReferenceElement)reference;
  }

  private static void showImportHint(@NotNull Editor editor, @NotNull PsiJavaCodeReferenceElement reference) {
    ImportHintDismissalTracker.showHint(editor, "import?", 0, 1, () -> true, reference, reference.getReferenceName());
  }

  private static @NotNull ImportClassFix createImportFix(@NotNull PsiJavaCodeReferenceElement reference)
    throws ExecutionException, InterruptedException {
    return ApplicationManager.getApplication()
      .executeOnPooledThread(() -> ReadAction.computeBlocking(() -> new ImportClassFix(reference)))
      .get();
  }
}
