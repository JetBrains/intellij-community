// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenameWrongRefHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class RenameFieldTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  protected void doTest(@NonNls String newFieldName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testName = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/renameField/before" + testName + ".java");
    myFixture.runInCommand(() -> {
      perform(newFieldName);
      myFixture.checkResultByFile("/refactoring/renameField/after" + testName + ".java");
    });
  }

  @Test
  public void testSimpleFieldRenaming() {
    doTest("myNewField");
  }

  @Test
  public void testCollisionsInMethod() {
    doTest("newFieldName");
  }

  @Test
  public void testCollisionsInMethodOfSubClass() {
    doTest("newFieldName");
  }

  @Test
  public void testCollisionsRenamingFieldWithSetter() {
    doTest("utm");
  }

  @Test
  public void testOverridingSetterParameterRenamed() {
    doTest("bar");
  }

  @Test
  public void testHidesOuter() {
    doTest("x");
  }

  @Test
  public void testEnumConstantWithConstructor() {
    doTest("newName");
  }

  @Test
  public void testEnumConstantWithInitializer() {  // IDEADEV-28840
    doTest("AAA");
  }

  @Test
  public void testNonNormalizedFields() { // IDEADEV-34344
    doTest("newField");
  }

  @Test
  public void testRenameWrongRefDisabled() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String suffix = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/renameField/before" + suffix + ".java");
    assertFalse(ReadAction.computeBlocking(() -> RenameWrongRefHandler.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile())));
  }

  @Test
  public void testFieldInColumns() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    // Assuming that test infrastructure setups temp settings (CodeStyleSettingsManager.setTemporarySettings()) and we don't
    // need to perform explicit cleanup at the test level.
    CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTest("jj");
  }

  @Test
  public void testRecordComponent() {
    doTest("baz");
  }

  @Test
  public void testRecordCanonicalConstructor() {
    doTest("baz");
  }

  @Test
  public void testRecordCompactConstructorReference() {
    doTest("baz");
  }

  @Test
  public void testRecordExplicitGetter() {
    doTest("baz");
  }

  @Test
  public void testRecordWithCanonicalConstructor() {
    doTest("baz");
  }

  @Test
  public void testRecordGetterOverloadPresent() {
    doTest("baz");
  }

  @Test
  public void testRecordComponentUsedInOuterClass() {
    doTest("baz");
  }

  @Test
  public void testRecordOverloads() {
    doTest("baz");
  }
  
  @Test
  public void testRenameInSnippet() {
    doTest("BAR");
  }

  @Test
  public void testRecordNonPhysicalAccessor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String testName = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/renameField/before" + testName + ".java");
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);

      assertNotNull(element);
      try {
        new MemberInplaceRenameHandler().doRename(element, myFixture.getEditor(), null);
        myFixture.type("hello");
      }
      finally {
        TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
        assertNotNull(state);
        state.gotoEnd(false);
      }
      myFixture.checkResultByFile("/refactoring/renameField/after" + testName + ".java");
    });
  }

  @Test
  public void testFieldOnlyInImplicitClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> {
      String suffix = myFixture.getTestName(false);
      myFixture.configureByFile("/refactoring/renameField/before" + suffix + ".java");
      myFixture.runInCommand(() -> {
        DataContext context = ((EditorEx)myFixture.getEditor()).getDataContext();
        List<? extends @NotNull RenameHandler> handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(context);
        assertEquals(1, handlers.size());
        RenameHandler renameHandler = handlers.get(0);
        renameHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), context);
        myFixture.checkResultByFile("/refactoring/renameField/after" + suffix + ".java");
      });
    });
  }

  private void perform(String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), flags);
    element = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myFixture.getEditor());
    assertNotNull(element);
    rename(element, newName);
  }

  /** Renames {@code element} with every automatic renamer; a subclass renames with another engine. */
  protected void rename(@NotNull PsiElement element, @NotNull String newName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    RenameProcessor processor = new RenameProcessor(myFixture.getProject(), element, newName, false, false);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
  }
}
