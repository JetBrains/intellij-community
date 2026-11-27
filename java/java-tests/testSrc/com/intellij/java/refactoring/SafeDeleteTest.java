// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.ModPlanJavaSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SafeDeleteTestSupport;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightSubFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class SafeDeleteTest {
  protected final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture =
    heavyJavaCodeInsightSubFixture("safeDeleteFixture", context -> new HeavyJavaCodeInsightFixture(context, getTestDataPath()) {
      @Override
      protected void setUp() {
        allowBazelOutputBase(getTestRootDisposable());
        super.setUp();
        ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
      }

      @Override
      protected @NotNull Sdk getTestProjectJdk() {
        return IdeaTestUtil.getMockJdk21();
      }
    });

  private boolean myDoCompare = true;

  /**
   * Under Bazel on Windows the first test indexes jars in {@code bazel-out} and {@code external} of the output base, which
   * {@link VfsRootAccess} does not allow there, so the test allows the output base of its runfiles: the parent of {@code execroot}.
   */
  private static void allowBazelOutputBase(@NotNull Disposable disposable) {
    String testSrcDir = System.getenv("TEST_SRCDIR");
    if (!BazelTestUtil.isUnderBazelTest() || testSrcDir == null) return;
    try {
      for (Path path = Path.of(testSrcDir).toRealPath(); path.getParent() != null; path = path.getParent()) {
        Path fileName = path.getFileName();
        if (fileName != null && fileName.toString().equals("execroot")) {
          VfsRootAccess.allowRootAccess(disposable, FileUtil.toSystemIndependentName(path.getParent().toString()));
          return;
        }
      }
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @return the real path of the test data: under Bazel the path can differ in case from the real one, which the case check of
   * {@link VfsTestUtil#findFileByCaseSensitivePath} rejects
   */
  private static @NotNull String getTestDataPath() {
    try {
      return Path.of(JavaTestUtil.getJavaTestDataPath()).toRealPath().toString();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static @NotNull String getTestRoot() {
    return "/refactoring/safeDelete/";
  }

  @Test
  public void testImplicitCtrCall() {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of constructor <b><code>Super.Super()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testImplicitCtrCall2() {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of constructor <b><code>Super.Super()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testMultipleInterfacesImplementation() {
    doTest("IFoo");
  }

  @Test
  public void testMultipleInterfacesImplementationThroughCommonInterface() {
    doTest("IFoo");
  }

  @Test
  public void testUsageInExtendsList() { doSingleFileTest(); }
  @Test
  public void testDeepDeleteParameterSimple() { doSingleFileTest(); }
  @Test
  public void testDeepDeleteParameterOtherTypeInBinaryExpression() { doSingleFileTest(); }
  @Test
  public void testDeepDeleteFieldAndAssignedParameter() { doSingleFileTest(); }
  @Test
  public void testImpossibleToDeepDeleteParameter() { doSingleFileTest(); }
  @Test
  public void testNoDeepDeleteParameterUsedInCallQualifier() { doSingleFileTest(); }
  @Test
  public void testNoDeepDeleteParameterUsedInNextArgumentExpression() { doSingleFileTest(); }
  @Test
  public void testToDeepDeleteParameterOverriders() { doSingleFileTest(); }
  @Test
  public void testDeleteParameterOfASiblingMethod() { doSingleFileTest(); }
  @Test
  public void testDeleteMethodCascade() { doSingleFileTest(); }
  @Test
  public void testDeleteMethodKeepEnumValues() { doSingleFileTest(); }
  @Test
  public void testDeleteMethodCascadeRecursive() { doSingleFileTest(); }
  @Test
  public void testDeleteMethodCascadeOverridden() { doSingleFileTest(); }
  @Test
  public void testDeleteParameterAndUpdateJavadocRef() { doSingleFileTest(); }
  @Test
  public void testDeleteConstructorParameterWithAnonymousClassUsage() { doSingleFileTest(); }
  @Test
  public void testAccidentalPropertyRef() { doSingleFileTest(); }
  @Test
  public void testMethodCannotBePrivate() { doSingleFileTest(); }
  @Test
  public void testNoRedundantImplements() { doSingleFileTest(); }

  @Test
  public void testRecordAccessorCannotBePrivate() {
    IdeaTestUtil.setModuleLanguageLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_16, heavyFixture.get().getTestRootDisposable());
    doSingleFileTest();
  }

  @Test
  public void testDeleteMethodWithPropertyUsage() {
    doTest("Foo");
  }

  @Test
  public void testDeleteClassWithPropertyUsage() {
    doTest("Foo");
  }

  @Test
  public void testDeleteMethodWithoutPropertyUsage() {
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return element instanceof PsiNamedElement named && named.getName().equals("a.b.c");
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return false;
      }
    }, heavyFixture.get().getTestRootDisposable());

    doTest("Foo");
  }

  @Test
  public void testParameterInHierarchy() {
    doTest("C2");
  }

  @Test
  public void testTopLevelDocComment() {
    doTest("foo.C1");
  }

  @Test
  public void testOverloadedMethods() {
    doTest("foo.A");
  }

  @Test
  public void testTopParameterInHierarchy() {
    doTest("I");
  }

  @Test
  public void testExtendsList() {
    doTest("B");
  }

  @Test
  public void testJavadocParamRef() {
    doTest("Super");
  }

  @Test
  public void testEnumConstructorParameter() {
    doTest("UserFlags");
  }

  @Test
  public void testSafeDeleteOfFieldAndParameterOfConstructor() {
    doSingleFileTest();
  }

  @Test
  public void testSafeDeleteStaticImports() {
    doTest("A");
  }

  @Test
  public void testSafeDeleteImports() {
    doTest("B");
  }

  @Test
  public void testSafeDeleteImportsOnInnerClasses() {
    doTest("p.B");
  }

  @Test
  public void testRemoveOverridersInspiteOfUnsafeUsages() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doTest("A"));
  }

  @Test
  public void testLocalVariable() {
    doTest("Super");
  }

  @Test
  public void testOverrideAnnotation() {
    doTest("Super");
  }

  @Test
  public void testSuperCall() {
    try {
      doTest("Super");
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>Super.foo()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testParameterFromFunctionalInterface() {
    try {
      IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of interface <b><code>SAM</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testFunctionalInterfaceMethod() {
    try {
      IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of interface <b><code>SAM</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testAmbiguityAfterParameterDelete() {
    try {
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>foo()</code></b> is already defined in class <b><code>Test</code></b>", e.getMessage());
    }
  }

  @Test
  public void testFunctionalInterfaceDefaultMethod() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_8);
    doSingleFileTest();
  }

  @Test
  public void testMethodDeepHierarchy() {
    doTest("Super");
  }

  @Test
  public void testInterfaceAsTypeParameterBound() {
    doSingleFileTest();
  }

  @Test
  public void testNestedTypeParameterBounds() {
    doSingleFileTest();
  }

  @Test
  public void testTypeParameterWithoutOwner() {
    doSingleFileTest();
  }

  @Test
  public void testLocalVariableSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of local variable <b><code>varName</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testParameterSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of parameter <b><code>i</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testUsageInGenerated() {
    doTest("A");
  }

  @Test
  public void testImplicitClass() {
    myDoCompare = false;
    try {
      doTest((rootDir, rootAfter) -> {
        heavyFixture.get().configureByExistingFile(rootDir.findFileByRelativePath("src/p/ImplicitClass.java"));
        performAction(Map.of());
      });
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("""
                     Usage of method <b><code>ImplicitClass.gasdfasdf()</code></b> that is not safe to delete.
                     Usage of method <b><code>ImplicitClass.gasdfasdf()</code></b> that is not safe to delete.""", e.getMessage());
    }
  }

  @Test
  public void testImplicitClass2() {
    myDoCompare = false;
    try {
      doTest((rootDir, rootAfter) -> {
        heavyFixture.get().configureByExistingFile(rootDir.findFileByRelativePath("src/p/ImplicitClass.java"));
        performAction(Map.of());
      });
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>Inner.x()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testFileWithTwoClasses() {
    myDoCompare = false;
    try {
      doTest((rootDir, rootAfter) -> {
        heavyFixture.get().configureByExistingFile(rootDir.findFileByRelativePath("src/p/TwoClasses.java"));
        SafeDeleteHandler.invoke(heavyFixture.get().getProject(), new PsiElement[]{heavyFixture.get().getFile()}, true);
      });
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("""
                     Usage of class <b><code>p.Next</code></b> that is not safe to delete.
                     Usage of class <b><code>p.TwoClasses</code></b> that is not safe to delete.""", e.getMessage());
    }
  }

  @Test
  public void testLastResourceVariable() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  @Test
  public void testLastResourceVariableConflictingVar() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  @Test
  public void testLastResourceVariableWithFinallyBlock() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  @Test
  public void testLastTypeParam() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  @Test
  public void testTypeParamFromDiamond() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_7);
    doSingleFileTest();
  }

  @Test
  public void testStripOverride() {
    doSingleFileTest();
  }

  @Test
  public void testEmptyIf() {
    doSingleFileTest();
  }

  @Test
  public void testTypeParameterWithinMethodHierarchy() {
    doSingleFileTest();
  }

  @Test
  public void testTypeParameterNoMethodHierarchy() {
    doSingleFileTest();
  }

  @Test
  public void testClassWithInnerStaticImport() {
    doTest("ClassWithInnerStaticImport");
  }

  @Test
  public void testInnerClassUsedInTheSameFile() {
    try {
      doSingleFileTest();
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of class <b><code>Test.Foo</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testConflictInInheritor() {
    try {
      doSingleFileTest();
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>foo()</code></b> is already defined in class <b><code>B</code></b>", e.getMessage());
    }
  }

  @Test
  public void testParameterInMethodUsedInMethodReference() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_8);
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doSingleFileTest());
  }

  @Test
  public void testNoConflictOnDeleteParameterWithMethodRefArg() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_8);
    doSingleFileTest();
  }

  @Test
  public void testShowConflictsButRemoveAnnotationsIfAnnotationTypeIsDeleted() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doSingleFileTest());
  }

  @Test
  public void testUsagesInScratch() throws Exception {
    BaseRefactoringProcessor.runWithDisabledPreview(() -> {
      VirtualFile scratchFile = ScratchRootType.getInstance()
        .createScratchFile(heavyFixture.get().getProject(), PathUtil.makeFileName("jScratch", "java"), JavaLanguage.INSTANCE,
                           "class jScratch {{//name()\n}}", ScratchFileService.Option.create_if_missing);
      try {
        doSingleFileTest(Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true));
      }
      finally {
        WriteAction.runAndWait(() -> scratchFile.delete(this));
      }
    });
  }

  @Test
  public void testDeepDeleteFieldAndInitializerMethod() { doSingleFileTest(); }
  @Test
  public void testDeleteMethodCascadeWithField() { doSingleFileTest(); }
  @Test
  public void testForInitExpr() { doSingleFileTest(); }
  @Test
  public void testForInitList() { doSingleFileTest(); }
  @Test
  public void testForInitDeclaration() { doSingleFileTest(); }
  @Test
  public void testForUpdateExpr() { doSingleFileTest(); }
  @Test
  public void testForUpdateList() { doSingleFileTest(); }
  @Test
  public void testUpdateContractOnParameterRemoval() { doSingleFileTest(); }
  @Test
  public void testMultipleFieldsInSingleDeclaration1() { doSingleFileTest(); }
  @Test
  public void testMultipleFieldsInSingleDeclaration2() { doSingleFileTest(); }
  @Test
  public void testMultipleFieldsInSingleDeclaration3() { doSingleFileTest(); }
  @Test
  public void testMultipleVariablesInSingleDeclaration1() { doSingleFileTest(); }
  @Test
  public void testMultipleVariablesInSingleDeclaration2() { doSingleFileTest(); }
  @Test
  public void testMultipleVariablesInSingleDeclaration3() { doSingleFileTest(); }

  @Test
  public void testSealedParent() {
    IdeaTestUtil.setModuleLanguageLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_17, heavyFixture.get().getTestRootDisposable());
    doSingleFileTest();
  }

  @Test
  public void testSealedGrandParent() {
    IdeaTestUtil.withLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_17, () -> doTest("Parent"));
  }

  @Test
  public void testRecordImplementsInterface() {
    IdeaTestUtil.setModuleLanguageLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_16, heavyFixture.get().getTestRootDisposable());
    doSingleFileTest();
  }

  @Test
  public void testRecordComponent() {
    IdeaTestUtil.setModuleLanguageLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_16, heavyFixture.get().getTestRootDisposable());
    doSingleFileTest();
  }

  @Test
  public void testRecordComponentCompactConstructor() {
    IdeaTestUtil.setModuleLanguageLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_16, heavyFixture.get().getTestRootDisposable());
    try {
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of record component <b><code>z</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testRecordComponentConflict() {
    IdeaTestUtil.setModuleLanguageLevel(heavyFixture.get().getModule(), LanguageLevel.JDK_16, heavyFixture.get().getTestRootDisposable());
    try {
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("""
                     Constructor <b><code>Point(int, int)</code></b> is already defined in record <b><code>Point</code></b>
                     Usage of record component <b><code>z</code></b> that is not safe to delete.""", e.getMessage());
    }
  }

  @Test
  public void testNonAccessibleGrandParent() {
    try {
      doTest("foo.Parent");
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of class <b><code>foo.Parent</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testMethodReference1() {
    try{
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>Safe.x(int)</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testMethodReference2() {
    try{
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method reference will be converted to a lambda expression", e.getMessage());
    }
  }

  @Test
  public void testLastClassInPackage() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_9);
    doTest("pack1.First");
  }

  @Test
  public void testNotLastClassInPackage() {
    IdeaTestUtil.setProjectLanguageLevel(heavyFixture.get().getProject(), LanguageLevel.JDK_1_9);
    doTest("pack1.First");
  }

  @Test
  public void testMentionInCommentAndString() {
    doSingleFileTest(Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true));
  }

  @Test
  public void testMentionInCommentSearchOff() {
    doSingleFileTest();
  }

  @Test
  public void testMentionInNonCodeFiles() {
    doTest("p.Gone", Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, true));
  }

  @Test
  public void testReferenceInPropertiesAndXml() {
    myDoCompare = false;
    try {
      doTest("p.Gone");
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("""
                     Usage of class <b><code>p.Gone</code></b> that is not safe to delete.
                     Usage of class <b><code>p.Gone</code></b> that is not safe to delete.""", e.getMessage());
    }
  }

  @Test
  public void testFieldWithAccessors() {
    doSingleFileTest();
  }

  @Test
  public void testSuperMethodDeleted() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true));
  }

  @Test
  public void testOverridersKept() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, false));
  }

  @Test
  public void testUnusedCalleesKept() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, false));
  }

  @Test
  public void testParameterInCallersKept() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, false));
  }

  @Test
  public void testSeveralMethods() { doSingleFileTest(); }

  @Test
  public void testSeveralMethodsCallingEachOther() { doSingleFileTest(); }

  @Test
  public void testSeveralMethodsOneUsed() {
    try {
      doSingleFileTest();
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>A.first()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  @Test
  public void testSeveralParameters() { doSingleFileTest(); }

  @Test
  public void testUnusedOverloadedConstructor() { doSingleFileTest(); }

  @Test
  public void testUnusedEnumConstant() { doSingleFileTest(); }

  @Test
  public void testUnusedAnnotationMethod() { doSingleFileTest(); }

  @Test
  public void testVarargsParameter() { doSingleFileTest(); }

  private void doTest(@NonNls String qClassName) {
    doTest(qClassName, Map.of());
  }

  /**
   * @param answers the answers to the questions of the deletion, on top of {@link #defaultAnswers()}
   */
  private void doTest(@NonNls String qClassName, @NotNull Map<ModPlanOption<?>, Object> answers) {
    doTest((_, _) -> performAction(qClassName, answers));
  }

  /**
   * Runs the test of {@code MultiFileTestCase.doTest} with the test data directory of the test name.
   */
  private void doTest(@NotNull HeavyJavaCodeInsightFixture.MultiFileAction action) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    fixture.doMultiFileTest(getTestRoot() + fixture.getTestName(true), this::prepareProject, myDoCompare, action);
  }

  private void prepareProject(VirtualFile rootDir) {
    VirtualFile src = rootDir.findChild("src");
    if (src == null) {
      heavyFixture.get().addSourceContentToRoots(heavyFixture.get().getModule(), rootDir);
    }
    else {
      PsiTestUtil.addContentRoot(heavyFixture.get().getModule(), rootDir);
      PsiTestUtil.addSourceRoot(heavyFixture.get().getModule(), src);
    }
    VirtualFile gen = rootDir.findChild("gen");
    if (gen != null) {
      PsiTestUtil.addSourceRoot(heavyFixture.get().getModule(), gen, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties("", true));
    }
  }

  private void doSingleFileTest() {
    doSingleFileTest(Map.of());
  }

  /**
   * @param answers the answers to the questions of the deletion, on top of {@link #defaultAnswers()}
   */
  private void doSingleFileTest(@NotNull Map<ModPlanOption<?>, Object> answers) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    try {
      fixture.configureByFile(getTestRoot() + fixture.getTestName(false) + ".java");
      EdtTestUtil.runInEdtAndWait(() -> {
        performAction(answers);
        fixture.checkResultByFile(getTestRoot() + fixture.getTestName(false) + "_after.java");
      });
    }
    catch (Exception e) {
      throw e instanceof RuntimeException r ? r : new RuntimeException(e);
    }
  }

  private void performAction(String qClassName, @NotNull Map<ModPlanOption<?>, Object> answers) {
    final PsiClass aClass = heavyFixture.get().getJavaFacade().findClass(qClassName, GlobalSearchScope.allScope(heavyFixture.get().getProject()));
    assertNotNull(aClass, "Class " + qClassName + " not found");
    heavyFixture.get().configureByExistingFile(aClass.getContainingFile().getVirtualFile());
    if (heavyFixture.get().getEditor().getCaretModel().getOffset() == 0) {
      heavyFixture.get().getEditor().getCaretModel().moveToOffset(aClass.getTextOffset());
    }
    performAction(answers);
  }

  private void performAction(@NotNull Map<ModPlanOption<?>, Object> testAnswers) {
    // Each caret marks an element; several carets delete their elements in one request.
    List<PsiElement> elements = new ArrayList<>();
    for (Caret caret : heavyFixture.get().getEditor().getCaretModel().getAllCarets()) {
      PsiElement element = TargetElementUtil.getInstance().findTargetElement(
        heavyFixture.get().getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, caret.getOffset());
      if (element != null && !elements.contains(element)) {
        elements.add(element);
      }
    }
    assertFalse(elements.isEmpty(), "No element found in text:\n" + heavyFixture.get().getFile().getText());
    Map<ModPlanOption<?>, Object> answers = new LinkedHashMap<>(defaultAnswers());
    answers.putAll(testAnswers);
    SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder = new SafeDeleteTestSupport.NonCodeUsageRecorder();
    SafeDeleteTestSupport.applyOptions(answers, nonCodeUsageRecorder, heavyFixture.get().getTestRootDisposable());
    performSafeDelete(elements, answers, nonCodeUsageRecorder);
    // The conflicts list the non-code usages in their messages, so the non-code usages are checked only without them.
    if (!BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
      SafeDeleteTestSupport.checkNonCodeUsages(nonCodeUsageRecorder, answers, nonCodeUsageFile());
    }
  }

  /**
   * @return the file with the expected non-code usages of the test. On the ModPlan engine a file {@code <name>.modPlan.mentions} goes
   * first: the ModPlan flows report the {@code exports} of an emptied package as non-code usages, where the legacy flow shows a
   * notification.
   */
  private @NotNull Path nonCodeUsageFile() {
    String pathWithoutExtension = getTestDataPath() + getTestRoot() + heavyFixture.get().getTestName(false);
    Path modPlanNonCodeUsageFile = Path.of(pathWithoutExtension + ".modPlan.mentions");
    return runsOnModPlan() && Files.exists(modPlanNonCodeUsageFile) ? modPlanNonCodeUsageFile : Path.of(pathWithoutExtension + ".mentions");
  }

  protected boolean runsOnModPlan() {
    return false;
  }

  /**
   * @return the answers of every test of the class, below the answers of the test
   */
  protected @NotNull Map<ModPlanOption<?>, Object> defaultAnswers() {
    return Map.of();
  }

  /**
   * Deletes {@code elements} in one request with {@code answers}, which {@link SafeDeleteTestSupport#applyOptions} already gave to the
   * interactive flows. A conflict throws {@link BaseRefactoringProcessor.ConflictsInTestsException} unless the test ignores the conflicts.
   *
   * @param nonCodeUsageRecorder gets the non-code usages of a deletion with no conflict
   */
  protected void performSafeDelete(@NotNull List<PsiElement> elements,
                                   @NotNull Map<ModPlanOption<?>, Object> answers,
                                   @NotNull SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder) {
    SafeDeleteHandler.invoke(heavyFixture.get().getProject(), elements.toArray(PsiElement.EMPTY_ARRAY), true);
  }
}