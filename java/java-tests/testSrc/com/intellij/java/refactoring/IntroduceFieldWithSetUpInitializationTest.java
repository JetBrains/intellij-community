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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.refactoring.introduceField.LocalToFieldHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class IntroduceFieldWithSetUpInitializationTest extends LightCodeInsightTestCase {
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      PsiTestUtil
        .addProjectLibrary(model, "JUnit4", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit4"));
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  public void testInSetUp() throws Exception {
    doTest();
  }

  public void testInitiallyInSetUp() throws Exception {
    doTest();
  }

  public void testPublicBaseClassSetUp() throws Exception {
    doTest();
  }

  public void testBeforeExist() throws Exception {
    doTest();
  }

  public void testBeforeNotExist() throws Exception {
    doTest();
  }

  public void testBeforeNotExist1() throws Exception {
    doTest();
  }

  public void testOrderInSetup() throws Exception {
    doTest();
  }

  public void testBeforeExistNonAnnotated() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    configureByFile("before" + getTestName(false) + ".java");
    final PsiLocalVariable local =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new LocalToFieldHandler(getProject(), false) {
      @Override
      protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(final PsiClass aClass,
                                                                            final PsiLocalVariable local,
                                                                            final PsiExpression[] occurences,
                                                                            final boolean isStatic) {
        return new BaseExpressionToFieldHandler.Settings("i", null, occurences, true, false, false,
                                                         BaseExpressionToFieldHandler.InitializationPlace.IN_SETUP_METHOD,
                                                         PsiModifier.PRIVATE, local, local.getType(), true, (BaseExpressionToFieldHandler.TargetDestination)null, false,
                                                         false);
      }
    }.convertLocalToField(local, myEditor);
    checkResultByFile("after" + getTestName(false)+ ".java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceField/";
  }
}
