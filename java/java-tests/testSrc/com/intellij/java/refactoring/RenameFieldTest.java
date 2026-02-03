// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
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
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RenameFieldTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected void doTest(@NonNls String newFieldName) {
    String testName = getTestName(false);
    configureByFile("/refactoring/renameField/before" + testName + ".java");
    perform(newFieldName);
    checkResultByFile("/refactoring/renameField/after" + testName + ".java");
  }

  public void testSimpleFieldRenaming() {
    doTest("myNewField");
  }

  public void testCollisionsInMethod() {
    doTest("newFieldName");
  }

  public void testCollisionsInMethodOfSubClass() {
    doTest("newFieldName");
  }

  public void testCollisionsRenamingFieldWithSetter() {
    doTest("utm");
  }

  public void testOverridingSetterParameterRenamed() {
    doTest("bar");
  }

  public void testHidesOuter() {
    doTest("x");
  }

  public void testEnumConstantWithConstructor() {
    doTest("newName");
  }

  public void testEnumConstantWithInitializer() {  // IDEADEV-28840
    doTest("AAA");
  }

  public void testNonNormalizedFields() { // IDEADEV-34344
    doTest("newField");
  }

  public void testRenameWrongRefDisabled() {
    String suffix = getTestName(false);
    configureByFile("/refactoring/renameField/before" + suffix + ".java");
    assertFalse(RenameWrongRefHandler.isAvailable(getProject(), getEditor(), getFile()));
  }

  public void testFieldInColumns() {
    // Assuming that test infrastructure setups temp settings (CodeStyleSettingsManager.setTemporarySettings()) and we don't
    // need to perform explicit cleanup at the test level.
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTest("jj");
  }

  public void testRecordComponent() {
    doTest("baz");
  }

  public void testRecordCanonicalConstructor() {
    doTest("baz");
  }

  public void testRecordCompactConstructorReference() {
    doTest("baz");
  }

  public void testRecordExplicitGetter() {
    doTest("baz");
  }

  public void testRecordWithCanonicalConstructor() {
    doTest("baz");
  }

  public void testRecordGetterOverloadPresent() {
    doTest("baz");
  }

  public void testRecordComponentUsedInOuterClass() {
    doTest("baz");
  }

  public void testRecordOverloads() {
    doTest("baz");
  }

  public void testRecordNonPhysicalAccessor() {
    String testName = getTestName(false);
    configureByFile("/refactoring/renameField/before" + testName + ".java");
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);

    assertNotNull(element);
    try {
      new MemberInplaceRenameHandler().doRename(element, getEditor(), null);
      type("hello");
    }
    finally {
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assertNotNull(state);
      state.gotoEnd(false);
    }
    checkResultByFile("/refactoring/renameField/after" + testName + ".java");
  }

  public void testFieldOnlyInImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> {
      String suffix = getTestName(false);
      configureByFile("/refactoring/renameField/before" + suffix + ".java");
      DataContext context = ((EditorEx)getEditor()).getDataContext();
      List<? extends @NotNull RenameHandler> handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(context);
      assertEquals(1, handlers.size());
      RenameHandler renameHandler = handlers.get(0);
      renameHandler.invoke(getProject(), getEditor(), getFile(), context);
      checkResultByFile("/refactoring/renameField/after" + suffix + ".java");
    });
  }

  private void perform(String newName) {
    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), flags);
    element = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditor());
    assertNotNull(element);

    RenameProcessor processor = new RenameProcessor(getProject(), element, newName, false, false);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
  }
}
