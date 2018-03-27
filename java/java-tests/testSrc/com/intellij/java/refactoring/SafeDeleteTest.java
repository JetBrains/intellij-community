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
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.PathUtil;
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

  public void testImplicitCtrCall() {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testImplicitCtrCall2() {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testMultipleInterfacesImplementation() {
    doTest("IFoo");
  }

  public void testMultipleInterfacesImplementationThroughCommonInterface() {
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
  
  public void testDeepDeleteFieldAndAssignedParameter() throws Exception {
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

  public void testParameterInHierarchy() {
    doTest("C2");
  }

  public void testTopLevelDocComment() {
    doTest("foo.C1");
  }

  public void testOverloadedMethods() {
    doTest("foo.A");
  }

  public void testTopParameterInHierarchy() {
    doTest("I");
  }

  public void testExtendsList() {
    doTest("B");
  }

  public void testJavadocParamRef() {
    doTest("Super");
  }

  public void testEnumConstructorParameter() {
    doTest("UserFlags");
  }

  public void testSafeDeleteStaticImports() {
    doTest("A");
  }

  public void testSafeDeleteImports() {
    doTest("B");
  }

  public void testSafeDeleteImportsOnInnerClasses() {
    doTest("p.B");
  }

  public void testRemoveOverridersInspiteOfUnsafeUsages() {
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doTest("A");
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testLocalVariable() {
    doTest("Super");
  }

  public void testOverrideAnnotation() {
    doTest("Super");
  }

  public void testSuperCall() {
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
      assertEquals("interface <b><code>SAM</code></b> has 1 usage that is not safe to delete.", message);
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
      assertEquals("interface <b><code>SAM</code></b> has 1 usage that is not safe to delete.", message);
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

  public void testMethodDeepHierarchy() {
    doTest("Super");
  }

  public void testInterfaceAsTypeParameterBound() throws Exception {
    doSingleFileTest();   
  }

  public void testNestedTypeParameterBounds() throws Exception {
    doSingleFileTest();
  }

  public void testTypeParameterWithoutOwner() throws Exception {
    doSingleFileTest();
  }

  public void testLocalVariableSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("local variable <b><code>varName</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testParameterSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertEquals("parameter <b><code>i</code></b> has 1 usage that is not safe to delete.", message);
    }
  }

  public void testUsageInGenerated() {
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

  public void testClassWithInnerStaticImport() {
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
    try {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
      doSingleFileTest();
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testNoConflictOnDeleteParameterWithMethodRefArg() throws Exception {
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

  public void testUsagesInScratch() throws Exception {
    BaseRefactoringProcessor.runWithDisabledPreview(() -> {
      VirtualFile scratchFile = ScratchRootType.getInstance()
        .createScratchFile(getProject(), PathUtil.makeFileName("jScratch", "java"), JavaLanguage.INSTANCE,
                           "class jScratch {{//name()\n}}", ScratchFileService.Option.create_if_missing);
      RefactoringSettings settings = RefactoringSettings.getInstance();
      boolean oldCommentsOption = settings.SAFE_DELETE_SEARCH_IN_COMMENTS;
      try {
        settings.SAFE_DELETE_SEARCH_IN_COMMENTS = true;
        doSingleFileTest();
      }
      finally {
        settings.SAFE_DELETE_SEARCH_IN_COMMENTS = oldCommentsOption;
        WriteAction.run(() -> scratchFile.delete(this));
      }
    });
  }

  public void testDeepDeleteFieldAndInitializerMethod() throws Exception {
    doSingleFileTest();
  }

  public void testDeleteMethodCascadeWithField() throws Exception {
    doSingleFileTest();
  }

  public void testForInitExpr() throws Exception {
    doSingleFileTest();
  }

  public void testForInitList() throws Exception {
    doSingleFileTest();
  }

  public void testForUpdateExpr() throws Exception {
    doSingleFileTest();
  }

  public void testForUpdateList() throws Exception {
    doSingleFileTest();
  }

  private void doTest(@NonNls final String qClassName) {
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

  private void performAction(final String qClassName) {
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