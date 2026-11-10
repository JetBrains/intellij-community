// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.codeInsight.daemon.impl.quickfix.ImportHintDismissalTracker;
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
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.EditorHintFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.HintHint;
import com.intellij.ui.LightweightHint;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ImportHintDismissalTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String UNRESOLVED_NAME = "ArrayList";

  private EditorHintFixture myHintFixture;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // a test shows no real hint window, so the Escape action stays disabled.
    // This fixture marks each new hint as visible, which enables the Escape action.
    myHintFixture = new EditorHintFixture(myFixture.getTestRootDisposable());
  }

  @Test
  public void testEscapeKeyKeepsTheImportHintHidden() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));
    ReadAction.runBlocking(() -> assertFalse(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME)));

    EdtTestUtil.runInEdtAndWait(() -> {
      showImportHint(editor, reference);
      assertNotNull(myHintFixture.getCurrentHintText());
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);

      assertNull(myHintFixture.getCurrentHintText());
      assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
    });
  }

  @Test
  public void testEscapeKeyKeepsTheHintHiddenForOneReferenceOnly() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList first; ArrayList second; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement firstReference = findReference(text.indexOf(UNRESOLVED_NAME));
    PsiJavaCodeReferenceElement secondReference = findReference(text.lastIndexOf(UNRESOLVED_NAME));
    assertNotSame(firstReference, secondReference);

    EdtTestUtil.runInEdtAndWait(() -> {
      showImportHint(editor, firstReference);
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);

      assertTrue(ImportHintDismissalTracker.isDismissed(editor, firstReference, UNRESOLVED_NAME));
      assertFalse(ImportHintDismissalTracker.isDismissed(editor, secondReference, UNRESOLVED_NAME));
    });
  }

  @Test
  public void testTypingInTheReferenceNameShowsTheHintAgain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList<caret> c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(myFixture.getFile().getText().indexOf(UNRESOLVED_NAME));

    EdtTestUtil.runInEdtAndWait(() -> {
      showImportHint(editor, reference);
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);
      assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));

      myFixture.type("s");
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      String changedName = UNRESOLVED_NAME + "s";
      PsiJavaCodeReferenceElement changedReference = findReference(myFixture.getFile().getText().indexOf(changedName));

      assertFalse(ImportHintDismissalTracker.isDismissed(editor, changedReference, changedName));
    });
  }

  @Test
  public void testHintWhichHidesWithoutTheEscapeKeyStaysAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));

    EdtTestUtil.runInEdtAndWait(() -> {
      showImportHint(editor, reference);
      HintManagerImpl.getInstanceImpl().hideAllHints();

      assertFalse(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
    });
  }

  @Test
  public void testClassImportHintIsNotShownAfterTheEscapeKey() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));

    ImportClassFix fix = createImportFix(reference);
    EdtTestUtil.runInEdtAndWait(() -> {
      assertEquals(ImportClassFixBase.Result.POPUP_SHOWN, fix.doFix(editor, true, true, false));

      showImportHint(editor, reference);
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, true);

      assertEquals(ImportClassFixBase.Result.POPUP_NOT_SHOWN, fix.doFix(editor, true, true, false));
    });
  }

  @Test
  public void testTheClientReportOfTheEscapeKeyKeepsTheImportHintHidden() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));

    EdtTestUtil.runInEdtAndWait(() -> {
      showImportHint(editor, reference);
      HintManagerImpl.getInstanceImpl().dismissCurrentQuestionHint(editor);

      assertNull(myHintFixture.getCurrentHintText());
      assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
    });
  }

  @Test
  public void testTheEscapeKeyKeepsTheHintHiddenAfterTheTooltipLeftTheScreen() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @Language("JAVA")
    String text = "class X { ArrayList c; }";
    myFixture.configureByText("X.java", text);
    Editor editor = myFixture.getEditor();
    PsiJavaCodeReferenceElement reference = findReference(text.indexOf(UNRESOLVED_NAME));
    Ref<LightweightHint> shownHint = new Ref<>();
    ApplicationManager.getApplication().getMessageBus().connect(myFixture.getTestRootDisposable())
      .subscribe(EditorHintListener.TOPIC, new EditorHintListener() {
        @Override
        public void hintShown(@NotNull Editor hintEditor, @NotNull LightweightHint hint, int flags, @NotNull HintHint hintInfo) {
          shownHint.set(hint);
        }
      });

    EdtTestUtil.runInEdtAndWait(() -> {
      showImportHint(editor, reference);
      // the tooltip can leave the screen before the key press, and the hint then stays on the hint stack.
      // The Escape action hides such a hint from its own enablement check
      shownHint.get().putUserData(LightweightHint.SHOWN_AT_DEBUG, Boolean.FALSE);
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_ESCAPE, false);

      assertTrue(ImportHintDismissalTracker.isDismissed(editor, reference, UNRESOLVED_NAME));
    });
  }

  private @NotNull PsiJavaCodeReferenceElement findReference(int offset) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiReference reference = ReadAction.computeBlocking(() -> myFixture.getFile().findReferenceAt(offset));
    assertInstanceOf(PsiJavaCodeReferenceElement.class, reference);
    return (PsiJavaCodeReferenceElement)reference;
  }

  private static void showImportHint(@NotNull Editor editor, @NotNull PsiJavaCodeReferenceElement reference) {
    ImportHintDismissalTracker.showHint(editor, "import?", 0, 1, () -> true, reference, reference.getReferenceName());
  }

  private static @NotNull ImportClassFix createImportFix(@NotNull PsiJavaCodeReferenceElement reference) {
    return ReadAction.computeBlocking(() -> new ImportClassFix(reference));
  }
}
