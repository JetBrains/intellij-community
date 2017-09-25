/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Functions;
import org.jetbrains.annotations.NotNull;

public class ChangeTypeSignatureTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/java/typeMigration/testData";
  }

  private void doTest(boolean success, String migrationTypeText) {
    String dataPath = "/refactoring/changeTypeSignature/";
    configureByFile(dataPath + getTestName(false) + ".java");
    final PsiFile file = getFile();
    final PsiElement element = file.findElementAt(getEditor().getCaretModel().getOffset());
    final PsiReferenceParameterList parameterList = PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class);
    assert parameterList != null;
    final PsiClass superClass = (PsiClass)((PsiJavaCodeReferenceElement)parameterList.getParent()).resolve();
    assert superClass != null;

    PsiType migrationType = getJavaFacade().getElementFactory().createTypeFromText(migrationTypeText, null);

    try {
      final TypeMigrationRules rules = new TypeMigrationRules();
      rules.setBoundScope(GlobalSearchScope.projectScope(getProject()));
      new TypeMigrationProcessor(getProject(),
                                 new PsiElement[]{parameterList},
                                 Functions.constant(PsiSubstitutor.EMPTY.put(superClass.getTypeParameters()[0], migrationType).substitute(new PsiImmediateClassType(superClass, PsiSubstitutor.EMPTY))),
                                 rules,
                                 true).run();
      if (success) {
        checkResultByFile(dataPath + getTestName(false) + ".java.after");
      } else {
        fail("Conflicts should be detected");
      }
    }
    catch (RuntimeException e) {
      if (success) {
        e.printStackTrace();
        fail("Conflicts should not appear");
      }
    }
  }

  private void doTest(boolean success) {
    doTest(success, CommonClassNames.JAVA_LANG_OBJECT);
  }

  public void testListTypeArguments() {
    doTest(true);
  }

  public void testFieldUsage() {
    doTest(true);
  }

  public void testFieldUsage1() {
    doTest(true);
  }

  public void testReturnType() {
    doTest(true);
  }

  public void testReturnType1() {
    doTest(true);
  }

  public void testReturnType2() {
    doTest(true);
  }

  public void testPassedParameter() {
    doTest(true);
  }

  public void testPassedParameter1() {
    doTest(true, "java.lang.Integer");
  }

  public void testPassedParameter2() {
    doTest(true);
  }

  public void testUsedInSuper() {
    doTest(true);
  }

  public void testCompositeReturnType() {
    doTest(true);
  }

  public void testTypeHierarchy() {
    doTest(true);
  }

  public void testTypeHierarchy1() {
    doTest(true);
  }

  public void testTypeHierarchy2() {
    doTest(true);
  }

  public void testTypeHierarchyFieldUsage() {
    doTest(true);
  }

  public void testTypeHierarchyFieldUsageConflict() {
    doTest(true);
  }

  public void testParameterMigration() {
    doTest(true);
  }

  public void testParameterMigration1() {
    doTest(true, "java.lang.Integer");
  }

  public void testParameterMigration2() {
    doTest(true, "java.lang.Integer");
  }

  public void testFieldTypeMigration() {
    doTest(true, "java.lang.String");
  }

  public void testMethodReturnTypeMigration() {
    doTest(true, "java.lang.Integer");
  }
}