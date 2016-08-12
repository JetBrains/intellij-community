/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

public class SafeDeleteTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/safeDelete/";
  }

  public void testImplicitCtrCall() throws Exception {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testImplicitCtrCall2() throws Exception {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testMultipleInterfacesImplementation() throws Exception {
    doTest("IFoo");
  }

  public void testMultipleInterfacesImplementationThroughCommonInterface() throws Exception {
    doTest("IFoo");
  }

  public void testUsageInExtendsList() throws Exception {
    doSingleFileTest();
  }

  public void testDeepDeleteParameterSimple() throws Exception {
    doSingleFileTest();
  }

  public void testDeepDeleteParameterOtherTypeInBinaryExpression() throws Exception {
    doSingleFileTest();
  }

  public void testImpossibleToDeepDeleteParameter() throws Exception {
    doSingleFileTest();
  }

  public void testNoDeepDeleteParameterUsedInCallQualifier() throws Exception {
    doSingleFileTest();
  }

  public void testNoDeepDeleteParameterUsedInNextArgumentExpression() throws Exception {
    doSingleFileTest();
  }

  public void testToDeepDeleteParameterOverriders() throws Exception {
    doSingleFileTest();
  }

  public void testDeleteMethodCascade() throws Exception {
    doSingleFileTest();
  }

  public void testDeleteMethodCascadeRecursive() throws Exception {
    doSingleFileTest();
  }

  public void testDeleteMethodCascadeOverridden() throws Exception {
    doSingleFileTest();
  }

  public void testDeleteParameterAndUpdateJavadocRef() throws Exception {
    doSingleFileTest();
  }

  public void testDeleteConstructorParameterWithAnonymousClassUsage() throws Exception {
    doSingleFileTest();
  }

  public void testParameterInHierarchy() throws Exception {
    doTest("C2");
  }


  public void testTopLevelDocComment() throws Exception {
    doTest("foo.C1");
  }

  public void testOverloadedMethods() throws Exception {
    doTest("foo.A");
  }

  public void testTopParameterInHierarchy() throws Exception {
    doTest("I");
  }

  public void testExtendsList() throws Exception {
    doTest("B");
  }

  public void testJavadocParamRef() throws Exception {
    doTest("Super");
  }

  public void testEnumConstructorParameter() throws Exception {
    doTest("UserFlags");
  }

  public void testSafeDeleteStaticImports() throws Exception {
    doTest("A");
  }

  public void testSafeDeleteImports() throws Exception {
    doTest("B");
  }

  public void testRemoveOverridersInspiteOfUnsafeUsages() throws Exception {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doTest("A");
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testLocalVariable() throws Exception {
    doTest("Super");
  }

  public void testOverrideAnnotation() throws Exception {
    doTest("Super");
  }

  public void testSuperCall() throws Exception {
    try {
      doTest("Super");
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("method <b><code>Super.foo()</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testParameterFromFunctionalInterface() throws Exception {
    try {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("class <b><code>SAM</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testFunctionalInterfaceMethod() throws Exception {
    try {
      LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("class <b><code>SAM</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testAmbiguityAfterParameterDelete() throws Exception {
    try {
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("Method foo() is already defined in the class <b><code>Test</code></b>", message);
    }
  }

  public void testFunctionalInterfaceDefaultMethod() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    doSingleFileTest();
  }

  public void testMethodDeepHierarchy() throws Exception {
    doTest("Super");
  }

  public void testInterfaceAsTypeParameterBound() throws Exception {
    doSingleFileTest();   
  }

  public void testLocalVariableSideEffect() throws Exception {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("local variable <b><code>varName</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testUsageInGenerated() throws Exception {
    doTest("A");
  }

  public void testLastResourceVariable() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testLastResourceVariableWithFinallyBlock() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testLastTypeParam() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testTypeParamFromDiamond() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testStripOverride() throws Exception {
    doSingleFileTest();
  }

  public void testEmptyIf() throws Exception {
    doSingleFileTest();
  }

  public void testTypeParameterWithinMethodHierarchy() throws Exception {
    doSingleFileTest();
  }
  
  public void testTypeParameterNoMethodHierarchy() throws Exception {
    doSingleFileTest();
  }

  public void testClassWithInnerStaticImport() throws Exception {
    doTest("ClassWithInnerStaticImport");
  }

  public void testInnerClassUsedInTheSameFile() throws Exception {
    try {
      doSingleFileTest();
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("class <b><code>Test.Foo</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testParameterInMethodUsedInMethodReference() throws Exception {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    doSingleFileTest();
  }

  public void testShowConflictsButRemoveAnnotationsIfAnnotationTypeIsDeleted() throws Exception {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doSingleFileTest();
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  private void doTest(@NonNls final String qClassName) throws Exception {
    doTest((rootDir, rootAfter) -> this.performAction(qClassName));
  }

  @Override
  protected void prepareProject(VirtualFile rootDir) {
    VirtualFile src = rootDir.findChild("src");
    if (src == null) {
      super.prepareProject(rootDir);
    }
    else {
      PsiTestUtil.addContentRoot(myModule, rootDir);
      PsiTestUtil.addSourceRoot(myModule, src);
    }
    VirtualFile gen = rootDir.findChild("gen");
    if (gen != null) {
      PsiTestUtil.addSourceRoot(myModule, gen, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties("", true));
    }
  }

  private void doSingleFileTest() throws Exception {
    configureByFile(getTestRoot() + getTestName(false) + ".java");
    performAction();
    checkResultByFile(getTestRoot() + getTestName(false) + "_after.java");
  }

  private void performAction(final String qClassName) throws Exception {
    final PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);
    configureByExistingFile(aClass.getContainingFile().getVirtualFile());

    performAction();
  }

  private void performAction() {
    final PsiElement psiElement = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull("No element found in text:\n" + getFile().getText(), psiElement);
    SafeDeleteHandler.invoke(getProject(), new PsiElement[]{psiElement}, true);
  }
}