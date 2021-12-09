// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class GotoDeclarationTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testContinue() { doTest(); }
  public void testContinueLabel() { doTest(); }
  public void testBreak() {  doTest(); }
  public void testBreak1() {  doTest(); }
  public void testBreakLabel() {  doTest(); }
  public void testAnonymous() {  doTest(); }

  private void performAction() {
    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertEquals(getFile(), element.getContainingFile());
    getEditor().getCaretModel().moveToOffset(element.getTextOffset());
    getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    getEditor().getSelectionModel().removeSelection();
  }

  private void doTest() {
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/continueAndBreak/" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/gotoDeclaration/continueAndBreak/" + name + "_after.java");
  }

  public void testGotoDirectory() {
    configure();
    PsiDirectory element = (PsiDirectory)GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertEquals("java.lang", JavaDirectoryService.getInstance().getPackage(element).getQualifiedName());
  }

  private void doTestMultipleConstructors() {
    configure();
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement[] elements =
      GotoDeclarationAction.findAllTargetElements(getProject(), getEditor(), offset);
    assertEquals(Arrays.asList(elements).toString(), 0, elements.length);

    final TargetElementUtil elementUtilBase = TargetElementUtil.getInstance();
    final PsiReference reference = getFile().findReferenceAt(offset);
    assertNotNull(reference);
    final Collection<PsiElement> candidates = elementUtilBase.getTargetCandidates(reference);
    assertEquals(candidates.toString(), 2, candidates.size());
  }

  public void testMultipleConstructors() {
    doTestMultipleConstructors();
  }

  public void testMultipleGenericConstructorsOnIncompleteCall() {
    doTestMultipleConstructors();
  }

  public void testMultipleConstructorsButArrayCreation() {
    configure();
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiReference reference = getFile().findReferenceAt(offset);
    assertNotNull(reference);
    final Collection<PsiElement> candidates = TargetElementUtil.getInstance().getTargetCandidates(reference);
    assertEquals(candidates.toString(), 1, candidates.size());
    final PsiElement item = ContainerUtil.getFirstItem(candidates);
    assertNotNull(item);
    assertTrue(item instanceof PsiClass && CommonClassNames.JAVA_LANG_STRING.equals(((PsiClass)item).getQualifiedName()));
  }

  public void testToStringInAnonymous() {
    configureFromFileText("A.java", "class A {{" +
                                    "       final Object o = new Object() {\n" +
                                    "            @Override\n" +
                                    "            public String toString() {\n" +
                                    "                return super.toString();\n" +
                                    "            }\n" +
                                    "        };\n" +
                                    "        o.to<caret>String();\n }}");
    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertInstanceOf(element, PsiMethod.class);
    PsiClass containingClass = ((PsiMethod)element).getContainingClass();
    assertInstanceOf(containingClass, PsiAnonymousClass.class);
  }

  public void testToFieldFromQualifierInNew() {
    configureFromFileText("A.java", "class A {Util myContext;\n" +
                                    "    private class Util {\n" +
                                    "        public class Filter {\n" +
                                    "            public Filter() {\n" +
                                    "            }\n" +
                                    "        }}\n" +
                                    "    private void method() {\n" +
                                    "        Util.Filter filter = my<caret>Context.new Filter();\n" +
                                    "    }}");
    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertInstanceOf(element, PsiField.class);
  }

  public void testArrayIndexNotCovered() {
    configureFromFileText("A.java", "class A {{ String[] arr; int index; arr[index]<caret>; }}");
    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertNull("Unexpected " + element, element);
  }

  public void testStringRefNotCovered() {
    configureFromFileText("A.java", "class A {{ foo(\"java\"<caret>); }}");
    int offset = getEditor().getCaretModel().getOffset();
    assertInstanceOf(GotoDeclarationAction.findTargetElement(getProject(), getEditor(), offset - 1), PsiDirectory.class);
    assertNull(GotoDeclarationAction.findTargetElement(getProject(), getEditor(), offset));
  }

  public void testEndOfFile() {
    configureFromFileText("A.java", "class A {{ String[] arr; arr<caret>");
    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertNotNull("Unexpected null", element);
  }

  public void testPatternMatchingGuardInSwitchExpression() {
    doTestGoToField();
  }

  public void testPatternMatchingGuardInSwitchStatement() {
    doTestGoToField();
  }

  public void testPatternMatchingWithParensAroundReference() {
    doTestGoToField();
  }


  public void testReferenceFieldInPatternMatchingInSwitchStatement() {
    doTestGoToField();
  }

  public void testCaseNullAfterPatternMatching() {
    doTestGoToPatternVariable();
  }

  public void testCaseNullAfterPatternMatchingExpr() {
    doTestGoToPatternVariable();
  }

  public void testDefaultAfterPatternMatching() {
    doTestGoToField();
  }

  public void testDefaultAfterPatternMatchingExpr() {
    doTestGoToField();
  }

  public void testGuardWithInstanceOfPatternMatchingInIf() {
    doTestGoToSecondPatternVariable();
  }

  public void testGuardWithInstanceOfPatternMatchingInSwitch() {
    doTestGoToSecondPatternVariable();
  }

  private void doTestGoToField() {
    configure();
    final PsiField field = PsiTreeUtil.findChildOfType(getFile(), PsiField.class);
    final PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertThat(element).isEqualTo(field);
  }

  private void doTestGoToPatternVariable() {
    configure();
    final PsiPatternVariable patternVariable = PsiTreeUtil.findChildOfType(getFile(), PsiPatternVariable.class);
    final PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertThat(element).isEqualTo(patternVariable);
  }

  private void doTestGoToSecondPatternVariable() {
    configure();
    final Iterator<PsiPatternVariable> iterator = PsiTreeUtil.findChildrenOfType(getFile(), PsiPatternVariable.class).iterator();
    iterator.next();
    final PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertThat(element).isEqualTo(iterator.next());
  }

  private void configure() {
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
  }
}
