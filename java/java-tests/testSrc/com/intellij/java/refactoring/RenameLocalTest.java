// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.JavaNameSuggestionProvider;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameWrongRefHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.testFramework.EditorTestUtil;
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

  @Override
  protected boolean isRunInCommand() {
    return false;
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

  public void testMethodNameUsedInMethodRefs() {
    doTest("bar1");
  }

  public void testRenameParamUniqueName() {
    configureByFile();
    final HashSet<String> result = new HashSet<>();
    new JavaNameSuggestionProvider().getSuggestedNames(getTargetElement(), getFile(), result);
    assertTrue(result.toString(), result.contains("window"));
  }

  private void doTest(final String newName) {
    configureByFile();
    new RenameProcessor(getProject(), getTargetElement(), newName, true, true).run();
    checkResultByFile();
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
  
  public void testRecordCanonicalConstructor() {
    doTest("Bar");
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

  public void testUndoAfterEditingOutsideOfTemplate() {
    configureByFile();
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    EditorTestUtil.testUndoInEditor(getEditor(), () -> {
      PsiElement element = getTargetElement();
      assertInPlaceRenameAllowedFor(element);
      new VariableInplaceRenameHandler().doRename(element, getEditor(),
                                                  DataManager.getInstance().getDataContext(getEditor().getComponent()));
      executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
      executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
      executeAction(IdeActions.ACTION_UNDO);
    });
    checkResultByFile();
  }

  public void testRenameWrongRef() {
    doRenameWrongRef("i");
  }

  private void doRenameWrongRef(final String newName) {
    configureByFile();

    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());

    new RenameWrongRefHandler().invoke(getProject(), getEditor(), getFile(), null);

    final TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
    assert state != null;
    final TextRange range = state.getCurrentVariableRange();
    assert range != null;

    WriteCommandAction.writeCommandAction(getProject())
                      .run(() -> getEditor().getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName));

    state.gotoEnd(false);
    checkResultByFile();
  }

  private void doTestInplaceRename(final String newName) {
    configureByFile();

    PsiElement element = getTargetElement();
    assertInPlaceRenameAllowedFor(element);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {
      CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), newName, getEditor(), element);
    }, null, null);

    checkResultByFile();
  }

  @NotNull
  private PsiElement getTargetElement() {
    final PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    return element;
  }

  private static void assertInPlaceRenameAllowedFor(PsiElement element) {
    assertTrue("In-place rename not allowed for " + element, JavaRefactoringSupportProvider.mayRenameInplace(element, null));
  }

  private void configureByFile() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
  }

  private void checkResultByFile() {
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}
