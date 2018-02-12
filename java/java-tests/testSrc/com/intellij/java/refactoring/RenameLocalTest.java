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
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.JavaNameSuggestionProvider;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameWrongRefHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author ven
 */
public class RenameLocalTest extends LightRefactoringTestCase {
  private static final String BASE_PATH = "/refactoring/renameLocal/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testIDEADEV3320() {
    doTest("f");
  }

  public void testIDEADEV13849() {
    doTest("aaaaa");
  }

  public void testConflictWithOuterClassField() {  // IDEADEV-24564
    doTest("f");
  }

  public void testConflictWithJavadocTag() {
    doTest("i");
  }

  public void testRenameLocalIncomplete() {
    doTest("_i");
  }

  public void testRenameParamIncomplete() {
    doTest("_i");
  }

  public void testClassNameUsedInMethodRefs() {
    doTest("Bar1");
  }

  public void testRenameParamUniqueName() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    final HashSet<String> result = new HashSet<>();
    new JavaNameSuggestionProvider().getSuggestedNames(element, getFile(), result);
    assertTrue(result.toString(), result.contains("window"));
  }

  private void doTest(final String newName) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    new RenameProcessor(getProject(), element, newName, true, true).run();
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testRenameInPlaceQualifyFieldReference() {
    doTestInplaceRename("myI");
  }
  
  public void testRenameInPlaceQualifyFieldReferenceInChild() {
    doTestInplaceRename("myI");
  }
  
  public void testRenameInPlaceThisNeeded() {
    doTestInplaceRename("a");
  }

  public void testRenameInPlaceOnRef() {
    doTestInplaceRename("a");
  }

  public void testRenameInPlaceParamInOverriderAutomaticRenamer() {
    doTestInplaceRename("pp");
  }
  
  public void testRenameFieldWithConstructorParamAutomatic() {
    doTest("pp");
  }

  public void testRenameInPlaceParamInOverriderAutomaticRenamerConflict() {
    doTestInplaceRename("pp");
  }

  public void testRenameResource() {
    doTest("r1");
  }

  public void testRenameResourceInPlace() {
    doTestInplaceRename("r1");
  }

  public void testRenameToFieldNameInStaticContext() {
    doTestInplaceRename("myFoo");
  }

  public void testRenameInPlaceInStaticContextWithConflictingField() {
    doTestInplaceRename("s");
  }

  private void doTestInplaceRename(final String newName) {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");

    final PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    assertTrue("In-place rename not allowed for " + element,
               JavaRefactoringSupportProvider.mayRenameInplace(element, null));

    CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), newName, getEditor(), element);

    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testRenameWrongRef() {
    doRenameWrongRef("i");
  }

  private void doRenameWrongRef(final String newName) {
    final String name = getTestName(false);
    configureByFile(BASE_PATH + name + ".java");

    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());

    new RenameWrongRefHandler().invoke(getProject(), getEditor(), getFile(), null);

    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assert state != null;
    final TextRange range = state.getCurrentVariableRange();
    assert range != null;

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        getEditor().getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName);
      }
    }.execute().throwException();

    state.gotoEnd(false);
    checkResultByFile(BASE_PATH + name + "_after.java");
  }
}
