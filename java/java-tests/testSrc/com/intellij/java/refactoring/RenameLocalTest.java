// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.ide.DataManager;
import com.intellij.lang.java.JavaRefactoringSupportProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.JavaNameSuggestionProvider;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameWrongRefHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class RenameLocalTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final String BASE_PATH = "/refactoring/renameLocal/";

  @Test
  public void testIDEADEV3320() {
    doTest("f");
  }

  @Test
  public void testIDEADEV13849() {
    doTest("aaaaa");
  }

  @Test
  public void testConflictWithOuterClassField() {  // IDEADEV-24564
    doTest("f");
  }

  @Test
  public void testConflictWithJavadocTag() {
    doTest("i");
  }

  @Test
  public void testRenameLocalIncomplete() {
    doTest("_i");
  }

  @Test
  public void testRenameParamIncomplete() {
    doTest("_i");
  }

  @Test
  public void testClassNameUsedInMethodRefs() {
    doTest("Bar1");
  }

  @Test
  public void testMethodNameUsedInMethodRefs() {
    doTest("bar1");
  }

  @Test
  public void testRenameParamUniqueName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile();
    final HashSet<String> result = new HashSet<>();
    ReadAction.runBlocking(() -> new JavaNameSuggestionProvider().getSuggestedNames(getTargetElement(), myFixture.getFile(), result));
    assertTrue(result.contains("window"), result.toString());
  }

  private void doTest(String newName) {
    configureByFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      rename(getTargetElement(), newName, false);
      checkResultByFile();
    });
  }

  @Test
  public void testRenameInPlaceQualifyFieldReference() {
    doTestInplaceRename("myI");
  }

  @Test
  public void testRenameInPlaceQualifyFieldReferenceInChild() {
    doTestInplaceRename("myI");
  }

  @Test
  public void testRenameInPlaceThisNeeded() {
    doTestInplaceRename("a");
  }

  @Test
  public void testRenameInPlaceOnRef() {
    doTestInplaceRename("a");
  }

  @Test
  public void testRenameInPlaceParamInOverriderAutomaticRenamer() {
    doTestInplaceRename("pp");
  }

  @Test
  public void testRenameFieldWithConstructorParamAutomatic() {
    configureByFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      rename(getTargetElement(), "pp", true);
      checkResultByFile();
    });
  }

  /**
   * Renames {@code element}, searching in comments and in text occurrences, with every automatic renamer if {@code withRenamers}; a
   * subclass renames with another engine.
   */
  protected void rename(@NotNull PsiElement element, @NotNull String newName, boolean withRenamers) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    RenameProcessor processor = new RenameProcessor(myFixture.getProject(), element, newName, true, true);
    if (withRenamers) {
      for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        processor.addRenamerFactory(factory);
      }
    }
    processor.run();
  }

  @Test
  public void testConflictWithPattern() {
    assertThatThrownBy(() -> doTest("s")).isInstanceOf(BaseRefactoringProcessor.ConflictsInTestsException.class).hasMessageContaining("An existing pattern variable <b><code>s</code></b> has the same name");
  }

  @Test
  public void testConflictWithPatternInline() {
    assertThatThrownBy(() -> doTestInplaceRename("s")).isInstanceOf(BaseRefactoringProcessor.ConflictsInTestsException.class).hasMessageContaining("Variable 's' Already Exists");
  }

  @Test
  public void testConflictInLambdaParameter() {
    assertThatThrownBy(() -> doTestInplaceRename("o")).isInstanceOf(BaseRefactoringProcessor.ConflictsInTestsException.class).hasMessageContaining("Variable 'o' Already Exists");
  }

  @Test
  public void testConflictWithFutureVar() {
    assertThatThrownBy(() -> doTest("y")).isInstanceOf(BaseRefactoringProcessor.ConflictsInTestsException.class).hasMessageContaining("An existing local variable <b><code>y</code></b> has the same name");
  }

  @Test
  public void testRenameInPlaceParamInOverriderAutomaticRenamerConflict() {
    doTestInplaceRename("pp");
  }

  @Test
  public void testRenameResource() {
    doTest("r1");
  }

  @Test
  public void testRecordCanonicalConstructor() {
    doTest("Bar");
  }

  @Test
  public void testRenameResourceInPlace() {
    doTestInplaceRename("r1");
  }

  @Test
  public void testRenameToFieldNameInStaticContext() {
    doTestInplaceRename("myFoo");
  }

  @Test
  public void testAnonymousClassFieldConflict() {
    try {
      doTestInplaceRename("field");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field 'field' Already Exists", e.getMessage());
    }
  }

  @Test
  public void testImplicitClassFieldConflict() {
    try {
      doTestInplaceRename("field");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field 'field' Already Exists", e.getMessage());
    }
  }

  @Test
  public void testRenameInPlaceInStaticContextWithConflictingField() {
    doTestInplaceRename("s");
  }

  @Test
  public void testUnnamedVariableInLambda() {
    doTestInplaceRename("pp");
  }

  @Test
  public void testUndoAfterEditingOutsideOfTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    EdtTestUtil.runInEdtAndWait(() -> {
      EditorTestUtil.testUndoInEditor(myFixture.getEditor(), () -> {
        PsiElement element = getTargetElement();
        assertInPlaceRenameAllowedFor(element);
        new VariableInplaceRenameHandler().doRename(element, myFixture.getEditor(),
                                                    DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent()));
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
        myFixture.performEditorAction(IdeActions.ACTION_UNDO);
      });
      checkResultByFile();
    });
  }

  @Test
  public void testRenameWrongRef() {
    doRenameWrongRef("i");
  }

  private void doRenameWrongRef(String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile();

    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());

    EdtTestUtil.runInEdtAndWait(() -> {
      new RenameWrongRefHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);

      final TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
      assert state != null;
      final TextRange range = state.getCurrentVariableRange();
      assert range != null;

      WriteCommandAction.writeCommandAction(myFixture.getProject())
                        .run(() -> myFixture.getEditor().getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), newName));

      state.gotoEnd(false);
      checkResultByFile();
    });
  }

  private void doTestInplaceRename(String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    configureByFile();

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement element = getTargetElement();
      assertInPlaceRenameAllowedFor(element);
      CodeInsightTestUtil.doInlineRename(new VariableInplaceRenameHandler(), newName, myFixture.getEditor(), element);

      checkResultByFile();
    });
  }

  private @NotNull PsiElement getTargetElement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiElement element = TargetElementUtil
      .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull(element);
    return element;
  }

  private static void assertInPlaceRenameAllowedFor(PsiElement element) {
    assertTrue(JavaRefactoringSupportProvider.mayRenameInplace(element, null), "In-place rename not allowed for " + element);
  }

  private void configureByFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
  }

  private void checkResultByFile() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
  }
}
