
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.navigation;

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

  public void testContinue() { doTest(); }
  public void testContinueLabel() { doTest(); }
  public void testBreak() {  doTest(); }
  public void testBreak1() {  doTest(); }
  public void testBreakLabel() {  doTest(); }
  public void testAnonymous() {  doTest(); }

  private static void performAction() {
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
    String name = getTestName(false);
    configureByFile("/codeInsight/gotoDeclaration/" + name + ".java");
    PsiDirectory element = (PsiDirectory)GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertEquals("java.lang", JavaDirectoryService.getInstance().getPackage(element).getQualifiedName());
  }

  public void testMultipleConstructors() {
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

  public void testMultipleConstructorsButArrayCreation() {
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