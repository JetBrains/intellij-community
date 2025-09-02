// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import org.junit.Assert;

public class RenameMethodMultiTest extends LightMultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/renameMethod/multi/";
  }

  public void testStaticImport1() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport2() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport3() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport4() {
    doTest("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testStaticImport5() {
    doAutomaticRenameMethod("pack1.A", "void staticMethod(int i)", "renamedStaticMethod");
  }

  public void testDefaultAnnotationMethod() {
    doTest("pack1.A", "int value()", "intValue");
  }

  public void testRename2OverrideFinal() {
    try {
      doTest("p.B", "void method()", "finalMethod");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      Assert.assertEquals("Method will override final method <b><code>finalMethod()</code></b> of super class <b><code>p.A</code></b>",
                          e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRename2HideFromAnonymous() {
    doTest("p.Foo", "void buzz(int i)", "bazz");
  }

  public void testAlignedMultilineParameters() {
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.ALIGN_MULTILINE_PARAMETERS = true;
    javaSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest("void test123(int i, int j)", "test123asd");
  }

  public void testAutomaticallyRenamedOverloads() {
    doAutomaticRenameMethod("p.Foo", "void foo()", "bar");
  }

  public void testOnlyChildMethod() {
    doTest("p.Foo", "void foo()", "bar");
  }

  public void testExpandStaticImportToAvoidConflictingResolve() {
    doTest("bar.Bar", "void createBar()", "bar");
  }

  private void doTest(String methodSignature, String newName) {
    doTest(getTestName(false), methodSignature, newName);
  }

  private void doTest(String className, String methodSignature, String newName) {
    doTest(() -> {
      final PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass);
      final PsiMethod methodBySignature = aClass.findMethodBySignature(getElementFactory().createMethodFromText(
                methodSignature + "{}", null), false);
      assertNotNull(methodBySignature);
      final RenameProcessor renameProcessor = new RenameProcessor(getProject(), methodBySignature, newName, false, false);
      renameProcessor.run();
    });
  }

  private void doAutomaticRenameMethod(String className, String methodSignature, String newName) {
    doTest(() -> {
      final PsiClass aClass = myFixture.getJavaFacade().findClass(className, GlobalSearchScope.moduleScope(getModule()));
      assertNotNull(aClass);
      final PsiMethod methodBySignature = aClass.findMethodBySignature(getElementFactory().createMethodFromText(
        methodSignature + "{}", null), false);
      assertNotNull(methodBySignature);

      final RenameProcessor processor = new RenameProcessor(getProject(), methodBySignature, newName, false, false);
      for (AutomaticRenamerFactory factory : AutomaticRenamerFactory.EP_NAME.getExtensionList()) {
        processor.addRenamerFactory(factory);
      }
      processor.run();
    });
  }
}
