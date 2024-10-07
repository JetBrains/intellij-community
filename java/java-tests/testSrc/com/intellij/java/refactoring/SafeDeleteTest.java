// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

public class SafeDeleteTest extends MultiFileTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk21();
  }

  @NotNull
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
      assertTrue(message, message.startsWith("Constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testImplicitCtrCall2() {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      String message = e.getMessage();
      assertTrue(message, message.startsWith("Constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
    }
  }

  public void testMultipleInterfacesImplementation() {
    doTest("IFoo");
  }

  public void testMultipleInterfacesImplementationThroughCommonInterface() {
    doTest("IFoo");
  }

  public void testUsageInExtendsList() {
    doSingleFileTest();
  }

  public void testDeepDeleteParameterSimple() {
    doSingleFileTest();
  }

  public void testDeepDeleteParameterOtherTypeInBinaryExpression() {
    doSingleFileTest();
  }

  public void testDeepDeleteFieldAndAssignedParameter() {
    doSingleFileTest();
  }

  public void testImpossibleToDeepDeleteParameter() {
    doSingleFileTest();
  }

  public void testNoDeepDeleteParameterUsedInCallQualifier() {
    doSingleFileTest();
  }

  public void testNoDeepDeleteParameterUsedInNextArgumentExpression() {
    doSingleFileTest();
  }

  public void testToDeepDeleteParameterOverriders() {
    doSingleFileTest();
  }

  public void testDeleteParameterOfASiblingMethod() {
    doSingleFileTest();
  }

  public void testDeleteMethodCascade() {
    doSingleFileTest();
  }

  public void testDeleteMethodKeepEnumValues() {
    doSingleFileTest();
  }

  public void testDeleteMethodCascadeRecursive() {
    doSingleFileTest();
  }

  public void testDeleteMethodCascadeOverridden() {
    doSingleFileTest();
  }

  public void testDeleteParameterAndUpdateJavadocRef() {
    doSingleFileTest();
  }

  public void testDeleteConstructorParameterWithAnonymousClassUsage() {
    doSingleFileTest();
  }

  public void testAccidentalPropertyRef() {
    doSingleFileTest();
  }

  public void testDeleteMethodWithPropertyUsage() {
    doTest("Foo");
  }

  public void testDeleteClassWithPropertyUsage() {
    doTest("Foo");
  }

  public void testDeleteMethodWithoutPropertyUsage() {
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return element instanceof PsiNamedElement && ((PsiNamedElement)element).getName().equals("a.b.c");
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
      }
    }, getTestRootDisposable());

    doTest("Foo");
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

  public void testSafeDeleteOfFieldAndParameterOfConstructor() {
    doSingleFileTest();
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
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doTest("A"));
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
      assertEquals("Method <b><code>Super.foo()</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testParameterFromFunctionalInterface() {
    try {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Interface <b><code>SAM</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testFunctionalInterfaceMethod() {
    try {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Interface <b><code>SAM</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testAmbiguityAfterParameterDelete() {
    try {
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method foo() is already defined in the class <b><code>Test</code></b>", e.getMessage());
    }
  }

  public void testFunctionalInterfaceDefaultMethod() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
    doSingleFileTest();
  }

  public void testMethodDeepHierarchy() {
    doTest("Super");
  }

  public void testInterfaceAsTypeParameterBound() {
    doSingleFileTest();
  }

  public void testNestedTypeParameterBounds() {
    doSingleFileTest();
  }

  public void testTypeParameterWithoutOwner() {
    doSingleFileTest();
  }

  public void testLocalVariableSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Local variable <b><code>varName</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testParameterSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Parameter <b><code>i</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testUsageInGenerated() {
    doTest("A");
  }

  public void testLastResourceVariable() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testLastResourceVariableConflictingVar() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testLastResourceVariableWithFinallyBlock() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testLastTypeParam() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testTypeParamFromDiamond() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  public void testStripOverride() {
    doSingleFileTest();
  }

  public void testEmptyIf() {
    doSingleFileTest();
  }

  public void testTypeParameterWithinMethodHierarchy() {
    doSingleFileTest();
  }

  public void testTypeParameterNoMethodHierarchy() {
    doSingleFileTest();
  }

  public void testClassWithInnerStaticImport() {
    doTest("ClassWithInnerStaticImport");
  }

  public void testInnerClassUsedInTheSameFile() {
    try {
      doSingleFileTest();
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Class <b><code>Test.Foo</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testConflictInInheritor() {
    try {
      doSingleFileTest();
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method foo() is already defined in the class <b><code>B</code></b>", e.getMessage());
    }
  }

  public void testParameterInMethodUsedInMethodReference() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doSingleFileTest());
  }

  public void testNoConflictOnDeleteParameterWithMethodRefArg() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
    doSingleFileTest();
  }

  public void testShowConflictsButRemoveAnnotationsIfAnnotationTypeIsDeleted() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doSingleFileTest());
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

  public void testDeepDeleteFieldAndInitializerMethod() {
    doSingleFileTest();
  }

  public void testDeleteMethodCascadeWithField() {
    doSingleFileTest();
  }

  public void testForInitExpr() {
    doSingleFileTest();
  }

  public void testForInitList() {
    doSingleFileTest();
  }

  public void testForUpdateExpr() {
    doSingleFileTest();
  }

  public void testForUpdateList() {
    doSingleFileTest();
  }

  public void testUpdateContractOnParameterRemoval() {
    doSingleFileTest();
  }

  public void testSealedParent() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_17, getTestRootDisposable());
    doSingleFileTest();
  }

  public void testSealedGrandParent() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_17, () -> doTest("Parent"));
  }

  public void testRecordImplementsInterface() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_16, getTestRootDisposable());
    doSingleFileTest();
  }

  public void testRecordComponent() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_16, getTestRootDisposable());
    doSingleFileTest();
  }

  public void testRecordComponentConflict() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_16, getTestRootDisposable());
    try {
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method Point(int, int) is already defined in the record <b><code>Point</code></b>\n" +
                   "Record component <b><code>z</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testNonAccessibleGrandParent() {
    try {
      doTest("foo.Parent");
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Class <b><code>foo.Parent</code></b> has 1 usage that is not safe to delete.", e.getMessage());
    }
  }

  public void testLastClassInPackage() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_9);
    doTest("pack1.First");
  }

  public void testNotLastClassInPackage() {
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_9);
    doTest("pack1.First");
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

  private void doSingleFileTest() {
    try {
      configureByFile(getTestRoot() + getTestName(false) + ".java");
      performAction();
      checkResultByFile(getTestRoot() + getTestName(false) + "_after.java");
    }
    catch (Exception e) {
      throw e instanceof RuntimeException r ? r : new RuntimeException(e);
    }
  }

  private void performAction(final String qClassName) {
    final PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);
    configureByExistingFile(aClass.getContainingFile().getVirtualFile());
    if (myEditor.getCaretModel().getOffset() == 0) {
      myEditor.getCaretModel().moveToOffset(aClass.getTextOffset());
    }
    performAction();
  }

  private void performAction() {
    final PsiElement psiElement = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertNotNull("No element found in text:\n" + getFile().getText(), psiElement);
    SafeDeleteHandler.invoke(getProject(), new PsiElement[]{psiElement}, true);
  }
}