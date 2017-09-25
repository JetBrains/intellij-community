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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * @author dsl
 */
public class RenameMethodMultiTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/renameMethod/multi/";
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
      Assert.assertEquals("Method finalMethod() will override \n" +
                          "a method of the base class <b><code>p.A</code></b>\n" +
                          "Renaming method will override final \"method <b><code>A.finalMethod()</code></b>\"", e.getMessage());
      return;
    }
    fail("Conflicts were not found");
  }

  public void testRename2HideFromAnonymous() {
    doTest("p.Foo", "void buzz(int i)", "bazz");
  }

  public void testAlignedMultilineParameters() {
    CommonCodeStyleSettings javaSettings = getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE);
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

  private void doTest(final String methodSignature, final String newName) {
    doTest(getTestName(false), methodSignature, newName);
  }

  private void doTest(final String className, final String methodSignature, final String newName) {
    doTest((rootDir, rootAfter) -> {
      final JavaPsiFacade manager = getJavaFacade();
      final PsiClass aClass = manager.findClass(className, GlobalSearchScope.moduleScope(myModule));
      assertNotNull(aClass);
      final PsiMethod methodBySignature = aClass.findMethodBySignature(manager.getElementFactory().createMethodFromText(
                methodSignature + "{}", null), false);
      assertNotNull(methodBySignature);
      final RenameProcessor renameProcessor = new RenameProcessor(myProject, methodBySignature, newName, false, false);
      renameProcessor.run();
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }

  private void doAutomaticRenameMethod(final String className, final String methodSignature, final String newName) {
    doTest((rootDir, rootAfter) -> {
      final JavaPsiFacade manager = getJavaFacade();
      final PsiClass aClass = manager.findClass(className, GlobalSearchScope.moduleScope(myModule));
      assertNotNull(aClass);
      final PsiMethod methodBySignature = aClass.findMethodBySignature(manager.getElementFactory().createMethodFromText(
        methodSignature + "{}", null), false);
      assertNotNull(methodBySignature);

      final RenameProcessor processor = new RenameProcessor(myProject, methodBySignature, newName, false, false);
      for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
        processor.addRenamerFactory(factory);
      }
      processor.run();
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }


}
