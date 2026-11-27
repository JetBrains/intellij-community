// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GotoDeclarationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testContinue() { doTest(); }

  @Test
  public void testContinueLabel() { doTest(); }

  @Test
  public void testBreak() { doTest(); }

  @Test
  public void testBreak1() { doTest(); }

  @Test
  public void testBreakLabel() { doTest(); }

  @Test
  public void testAnonymous() { doTest(); }

  @Test
  public void testFromGuardToDestructuringPattern() { doGotoTest(); }

  @Test
  public void testFromArrowToDestructuringPattern() { doGotoTest(); }

  @Test
  public void testFromStatementToDestructuringPattern() { doGotoTest(); }

  @Test
  public void testFromIfToDestructuringPattern() { doGotoTest(); }

  @Test
  public void testToGuardedTypeTest() { doGotoTest(); }

  @Test
  public void testRecordPatternInForEach() { doGotoTest(); }

  private void doGotoTest() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    String name = myFixture.getTestName(false);
    daemonFixture.get().configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(this::performAction);
    myFixture.checkResultByFile("/codeInsight/gotoDeclaration/" + name + "_after.java");
  }

  private void performAction() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Editor editor = myFixture.getEditor();
    PsiElement element = GotoDeclarationAction.findTargetElement(myFixture.getProject(), editor, editor.getCaretModel().getOffset());
    assertEquals(myFixture.getFile(), element.getContainingFile());
    editor.getCaretModel().moveToOffset(element.getTextOffset());
    editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    editor.getSelectionModel().removeSelection();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    String name = myFixture.getTestName(false);
    daemonFixture.get().configureByFile("/codeInsight/gotoDeclaration/continueAndBreak/" + name + ".java");
    EdtTestUtil.runInEdtAndWait(this::performAction);
    myFixture.checkResultByFile("/codeInsight/gotoDeclaration/continueAndBreak/" + name + "_after.java");
  }

  @Test
  public void testGotoDirectory() {
    configure();
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Editor editor = myFixture.getEditor();
    ReadAction.runBlocking(() -> {
      PsiDirectory element = (PsiDirectory)GotoDeclarationAction.findTargetElement(myFixture.getProject(), editor, editor.getCaretModel().getOffset());
      assertEquals("java.lang", JavaDirectoryService.getInstance().getPackage(element).getQualifiedName());
    });
  }

  private void doTestMultipleConstructors() {
    configure();
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Editor editor = myFixture.getEditor();
    ReadAction.runBlocking(() -> {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement[] elements =
        GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), editor, offset);
      assertEquals(0, elements.length, Arrays.asList(elements).toString());

      final TargetElementUtil elementUtilBase = TargetElementUtil.getInstance();
      final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference);
      final Collection<PsiElement> candidates = elementUtilBase.getTargetCandidates(reference);
      assertEquals(2, candidates.size(), candidates.toString());
    });
  }

  @Test
  public void testMultipleConstructors() {
    doTestMultipleConstructors();
  }

  @Test
  public void testMultipleGenericConstructorsOnIncompleteCall() {
    doTestMultipleConstructors();
  }

  @Test
  public void testMultipleConstructorsButArrayCreation() {
    configure();
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    ReadAction.runBlocking(() -> {
      final int offset = myFixture.getEditor().getCaretModel().getOffset();
      final PsiReference reference = myFixture.getFile().findReferenceAt(offset);
      assertNotNull(reference);
      final Collection<PsiElement> candidates = TargetElementUtil.getInstance().getTargetCandidates(reference);
      assertEquals(1, candidates.size(), candidates.toString());
      final PsiElement item = ContainerUtil.getFirstItem(candidates);
      assertNotNull(item);
      assertTrue(item instanceof PsiClass && CommonClassNames.JAVA_LANG_STRING.equals(((PsiClass)item).getQualifiedName()));
    });
  }

  @Test
  public void testToStringInAnonymous() {
    daemonFixture.get().configureFromFileText("A.java", """
      class A {{       final Object o = new Object() {
                  @Override
                  public String toString() {
                      return super.toString();
                  }
              };
              o.to<caret>String();
       }}""");
    ReadAction.runBlocking(() -> {
      PsiElement element = findTargetElementAtCaret();
      assertInstanceOf(PsiMethod.class, element);
      PsiClass containingClass = ((PsiMethod)element).getContainingClass();
      assertInstanceOf(PsiAnonymousClass.class, containingClass);
    });
  }

  @Test
  public void testToFieldFromQualifierInNew() {
    daemonFixture.get().configureFromFileText("A.java", """
      class A {Util myContext;
          private class Util {
              public class Filter {
                  public Filter() {
                  }
              }}
          private void method() {
              Util.Filter filter = my<caret>Context.new Filter();
          }}""");
    PsiElement element = ReadAction.computeBlocking(this::findTargetElementAtCaret);
    assertInstanceOf(PsiField.class, element);
  }

  @Test
  public void testArrayIndexNotCovered() {
    daemonFixture.get().configureFromFileText("A.java", "class A {{ String[] arr; int index; arr[index]<caret>; }}");
    PsiElement element = ReadAction.computeBlocking(this::findTargetElementAtCaret);
    assertNull(element, "Unexpected " + element);
  }

  @Test
  public void testStringRefNotCovered() {
    daemonFixture.get().configureFromFileText("A.java", "class A {{ foo(\"java\"<caret>); }}");
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Project project = myFixture.getProject();
    Editor editor = myFixture.getEditor();
    ReadAction.runBlocking(() -> {
      int offset = editor.getCaretModel().getOffset();
      assertInstanceOf(PsiDirectory.class, GotoDeclarationAction.findTargetElement(project, editor, offset - 1));
      assertNull(GotoDeclarationAction.findTargetElement(project, editor, offset));
    });
  }

  @Test
  public void testEndOfFile() {
    daemonFixture.get().configureFromFileText("A.java", "class A {{ String[] arr; arr<caret>");
    PsiElement element = ReadAction.computeBlocking(this::findTargetElementAtCaret);
    assertNotNull(element, "Unexpected null");
  }

  @Test
  public void testPatternMatchingGuardInSwitchExpression() {
    doTestGoToField();
  }

  @Test
  public void testPatternMatchingGuardInSwitchStatement() {
    doTestGoToField();
  }

  @Test
  public void testPatternMatchingWithParensAroundReference() {
    doTestGoToField();
  }


  @Test
  public void testReferenceFieldInPatternMatchingInSwitchStatement() {
    doTestGoToField();
  }

  @Test
  public void testCaseNullAfterPatternMatching() {
    IdeaTestUtil.withLevel(daemonFixture.get().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTestGoToField());
  }

  @Test
  public void testCaseNullAfterPatternMatchingExpr() {
    doTestGoToPatternVariable();
  }

  @Test
  public void testDefaultAfterPatternMatching() {
    doTestGoToField();
  }

  @Test
  public void testDefaultAfterPatternMatchingExpr() {
    doTestGoToField();
  }

  private void doTestGoToField() {
    PsiFile file = configure();
    ReadAction.runBlocking(() -> {
      final PsiField field = PsiTreeUtil.findChildOfType(file, PsiField.class);
      final PsiElement element = findTargetElementAtCaret();
      assertThat(element).isEqualTo(field);
    });
  }

  private void doTestGoToPatternVariable() {
    PsiFile file = configure();
    ReadAction.runBlocking(() -> {
      final PsiPatternVariable patternVariable = PsiTreeUtil.findChildOfType(file, PsiPatternVariable.class);
      final PsiElement element = findTargetElementAtCaret();
      assertThat(element).isEqualTo(patternVariable);
    });
  }

  private void doTestGoToSecondPatternVariable() {
    PsiFile file = configure();
    ReadAction.runBlocking(() -> {
      final Iterator<PsiPatternVariable> iterator = PsiTreeUtil.findChildrenOfType(file, PsiPatternVariable.class).iterator();
      iterator.next();
      final PsiElement element = findTargetElementAtCaret();
      assertThat(element).isEqualTo(iterator.next());
    });
  }

  private PsiElement findTargetElementAtCaret() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    Editor editor = myFixture.getEditor();
    return GotoDeclarationAction.findTargetElement(myFixture.getProject(), editor, editor.getCaretModel().getOffset());
  }

  private PsiFile configure() {
    String name = daemonFixture.get().getFixture().getTestName(false);
    return daemonFixture.get().configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
  }
}
