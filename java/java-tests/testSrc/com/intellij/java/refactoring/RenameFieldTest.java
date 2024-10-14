// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.*;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RenameFieldTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected void doTest(@NonNls String newName, @NonNls String ext) {
    String suffix = getTestName(false);
    configureByFile("/refactoring/renameField/before" + suffix + "." + ext);
    perform(newName);
    checkResultByFile("/refactoring/renameField/after" + suffix + "." + ext);
  }

  public void testSimpleFieldRenaming() {
    doTest("myNewField", "java");
  }

  public void testCollisionsInMethod() {
    doTest("newFieldName", "java");
  }

  public void testCollisionsInMethodOfSubClass() {
    doTest("newFieldName", "java");
  }

  public void testCollisionsRenamingFieldWithSetter() {
    doTest("utm", "java");
  }

  public void testOverridingSetterParameterRenamed() {
    doTest("bar", "java");
  }

  public void testHidesOuter() {
    doTest("x", "java");
  }

  public void testEnumConstantWithConstructor() {
    doTest("newName", "java");
  }

  public void testEnumConstantWithInitializer() {  // IDEADEV-28840
    doTest("AAA", "java");
  }

  public void testNonNormalizedFields() { // IDEADEV-34344
    doTest("newField", "java");
  }

  public void testRenameWrongRefDisabled() {
    String suffix = getTestName(false);
    configureByFile("/refactoring/renameField/before" + suffix + ".java");
    assertFalse(RenameWrongRefHandler.isAvailable(getProject(), getEditor(), getFile()));
  }

  public void testFieldInColumns() {
    // Assuming that test infrastructure setups temp settings (CodeStyleSettingsManager.setTemporarySettings()) and we don't
    // need to perform explicit clean-up at the test level.
    CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTest("jj", "java");
  }

  public void testRecordComponent() {
    doTest("baz", "java");
  }

  public void testRecordCanonicalConstructor() {
    doTest("baz", "java");
  }

  public void testRecordCompactConstructorReference() {
    doTest("baz", "java");
  }

  public void testRecordExplicitGetter() {
    doTest("baz", "java");
  }

  public void testRecordWithCanonicalConstructor() {
    doTest("baz", "java");
  }

  public void testRecordGetterOverloadPresent() {
    doTest("baz", "java");
  }
  public void testRecordComponentUsedInOuterClass() {
    doTest("baz", "java");
  }

  public void testRecordOverloads() {
    doTest("baz", "java");
  }

  public void testFieldOnlyInImplicitClass() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), () -> {
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
    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil
                                                                            .ELEMENT_NAME_ACCEPTED |
                                                                          TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    element = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, getEditor());
    assertNotNull(element);

    RenameProcessor processor = new RenameProcessor(getProject(), element, newName, false, false);
    for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
      processor.addRenamerFactory(factory);
    }
    processor.run();
  }
}
