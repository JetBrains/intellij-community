
package com.intellij.codeInsight.navigation;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class GotoDeclarationTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testContinue() throws Exception { doTest(); }
  public void testContinueLabel() throws Exception { doTest(); }
  public void testBreak() throws Exception {  doTest(); }
  public void testBreak1() throws Exception {  doTest(); }
  public void testBreakLabel() throws Exception {  doTest(); }
  public void testAnonymous() throws Exception {  doTest(); }

  private static void performAction() {
    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertEquals(getFile(), element.getContainingFile());
    getEditor().getCaretModel().moveToOffset(element.getTextOffset());
    getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    getEditor().getSelectionModel().removeSelection();
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/continueAndBreak/" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/gotoDeclaration/continueAndBreak/" + name + "_after.java");
  }

  public void testGotoDirectory() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
    PsiDirectory element = (PsiDirectory)GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertEquals("java.lang", JavaDirectoryService.getInstance().getPackage(element).getQualifiedName());
  }

  public void testMultipleConstructors() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
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

  public void testMultipleConstructorsButArrayCreation() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiReference reference = getFile().findReferenceAt(offset);
    assertNotNull(reference);
    final Collection<PsiElement> candidates = TargetElementUtil.getInstance().getTargetCandidates(reference);
    assertEquals(candidates.toString(), 1, candidates.size());
    final PsiElement item = ContainerUtil.getFirstItem(candidates);
    assertNotNull(item);
    assertTrue(item instanceof PsiClass && CommonClassNames.JAVA_LANG_STRING.equals(((PsiClass)item).getQualifiedName()));
  }
}