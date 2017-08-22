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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameWrongRefHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

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
    CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    doTest("jj", "java");
  }
  
  protected static void perform(String newName) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                         .ELEMENT_NAME_ACCEPTED |
                                                                       TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

    new RenameProcessor(getProject(), element, newName, false, false).run();
  }
}
