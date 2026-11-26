// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.intellij.refactoring;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceParameterList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.Functions;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ChangeTypeSignatureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/typeMigration/testData");

  private void doTest(boolean success, String migrationTypeText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String dataPath = "/refactoring/changeTypeSignature/";
    myFixture.configureByFile(dataPath + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      final PsiFile file = myFixture.getFile();
      final PsiElement element = file.findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      final PsiReferenceParameterList parameterList = PsiTreeUtil.getParentOfType(element, PsiReferenceParameterList.class);
      assert parameterList != null;
      final PsiClass superClass = (PsiClass)((PsiJavaCodeReferenceElement)parameterList.getParent()).resolve();
      assert superClass != null;

      PsiType migrationType = myFixture.getJavaFacade().getElementFactory().createTypeFromText(migrationTypeText, null);

      try {
        final TypeMigrationRules rules = new TypeMigrationRules(myFixture.getProject());
        rules.setBoundScope(GlobalSearchScope.projectScope(myFixture.getProject()));
        new TypeMigrationProcessor(myFixture.getProject(),
                                   new PsiElement[]{parameterList},
                                   Functions.constant(PsiSubstitutor.EMPTY.put(superClass.getTypeParameters()[0], migrationType).substitute(new PsiImmediateClassType(superClass, PsiSubstitutor.EMPTY))),
                                   rules,
                                   true).run();
        if (success) {
          myFixture.checkResultByFile(dataPath + myFixture.getTestName(false) + ".java.after");
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
    });
  }

  private void doTest(boolean success) {
    doTest(success, CommonClassNames.JAVA_LANG_OBJECT);
  }

  @Test
  public void testListTypeArguments() {
    doTest(true);
  }

  @Test
  public void testFieldUsage() {
    doTest(true);
  }

  @Test
  public void testFieldUsage1() {
    doTest(true);
  }

  @Test
  public void testReturnType() {
    doTest(true);
  }

  @Test
  public void testReturnType1() {
    doTest(true);
  }

  @Test
  public void testReturnType2() {
    doTest(true);
  }

  @Test
  public void testPassedParameter() {
    doTest(true);
  }

  @Test
  public void testPassedParameter1() {
    doTest(true, "java.lang.Integer");
  }

  @Test
  public void testPassedParameter2() {
    doTest(true);
  }

  @Test
  public void testUsedInSuper() {
    doTest(true);
  }

  @Test
  public void testCompositeReturnType() {
    doTest(true);
  }

  @Test
  public void testTypeHierarchy() {
    doTest(true);
  }

  @Test
  public void testTypeHierarchy1() {
    doTest(true);
  }

  @Test
  public void testTypeHierarchy2() {
    doTest(true);
  }

  @Test
  public void testTypeHierarchyFieldUsage() {
    doTest(true);
  }

  @Test
  public void testTypeHierarchyFieldUsageConflict() {
    doTest(true);
  }

  @Test
  public void testParameterMigration() {
    doTest(true);
  }

  @Test
  public void testParameterMigration1() {
    doTest(true, "java.lang.Integer");
  }

  @Test
  public void testParameterMigration2() {
    doTest(true, "java.lang.Integer");
  }

  @Test
  public void testFieldTypeMigration() {
    doTest(true, "java.lang.String");
  }

  @Test
  public void testMethodReturnTypeMigration() {
    doTest(true, "java.lang.Integer");
  }
}