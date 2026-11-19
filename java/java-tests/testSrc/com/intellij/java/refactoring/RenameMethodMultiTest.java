// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class RenameMethodMultiTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameMethod/multi/");

  @Test
  public void testStaticImport1() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  @Test
  public void testStaticImport2() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  @Test
  public void testStaticImport3() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  @Test
  public void testStaticImport4() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  @Test
  public void testStaticImport5() {
    doAutomaticRenameMethod("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  @Test
  public void testDefaultAnnotationMethod() {
    doTest("pack1.A", "int value()", "intValue");
  }

  @Test
  public void testRename2OverrideFinal() {
    try {
      doTest("p.B", "void method()", "finalMethod");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method will override final method <b><code>finalMethod()</code></b> of super class <b><code>p.A</code></b>",
                          e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  @Test
  public void testRename2HideFromAnonymous() {
    doTest("p.Foo", "void buzz(int i)", "bazz");
  }

  @Test
  public void testAlignedMultilineParameters() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_PARAMETERS = true;
    javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("void test123(int i, int j)", "test123asd");
  }

  @Test
  public void testAutomaticallyRenamedOverloads() {
    doAutomaticRenameMethod("p.Foo", "void foo()", "bar");
  }

  @Test
  public void testOnlyChildMethod() {
    doTest("p.Foo", "void foo()", "bar");
  }

  @Test
  public void testExpandStaticImportToAvoidConflictingResolve() {
    doTest("bar.Bar", "void createBar()", "bar");
  }

  private void doTest(String methodSignature, String newName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTest(myFixture.getTestName(false), methodSignature, newName);
  }

  private void doTest(String className, String methodSignature, String newName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      final PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass);
      final PsiMethod methodBySignature = aClass.findMethodBySignature(JavaPsiFacade.getElementFactory(myFixture.getProject()).createMethodFromText(
                methodSignature + "{}", null), false);
      assertNotNull(methodBySignature);
      final RenameProcessor renameProcessor = new RenameProcessor(myFixture.getProject(), methodBySignature, newName, false, false);
      renameProcessor.run();
    });
  }

  private void doAutomaticRenameMethod(String className, String methodSignature, String newName) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    multiFile.get().doTest(() -> {
      final PsiClass aClass = myFixture.getJavaFacade().findClass(className, GlobalSearchScope.moduleScope(myFixture.getModule()));
      assertNotNull(aClass);
      final PsiMethod methodBySignature = aClass.findMethodBySignature(JavaPsiFacade.getElementFactory(myFixture.getProject()).createMethodFromText(
        methodSignature + "{}", null), false);
      assertNotNull(methodBySignature);

      final RenameProcessor processor = new RenameProcessor(myFixture.getProject(), methodBySignature, newName, false, false);
      for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        processor.addRenamerFactory(factory);
      }
      processor.run();
    });
  }
}
