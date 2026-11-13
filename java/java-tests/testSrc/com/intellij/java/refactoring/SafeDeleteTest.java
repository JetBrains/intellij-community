// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
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
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.ModPlanJavaSafeDeleteOptions;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.SafeDeleteTestSupport;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SafeDeleteTest extends MultiFileTestCase {
  @Override
  protected void setUp() throws Exception {
    allowBazelOutputBase();
    super.setUp();
    ModuleRootModificationUtil.updateModel(getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk21();
  }

  /**
   * Under Bazel on Windows the first test indexes jars in {@code bazel-out} and {@code external} of the output base, which
   * {@link VfsRootAccess} does not allow there, so the test allows the output base of its runfiles: the parent of {@code execroot}.
   */
  private void allowBazelOutputBase() throws IOException {
    String testSrcDir = System.getenv("TEST_SRCDIR");
    if (!BazelTestUtil.isUnderBazelTest() || testSrcDir == null) return;
    for (Path path = Path.of(testSrcDir).toRealPath(); path.getParent() != null; path = path.getParent()) {
      Path fileName = path.getFileName();
      if (fileName != null && fileName.toString().equals("execroot")) {
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), FileUtil.toSystemIndependentName(path.getParent().toString()));
        return;
      }
    }
  }

  /**
   * @return the real path of the test data: under Bazel the path can differ in case from the real one, which the case check of
   * {@link VfsTestUtil#findFileByCaseSensitivePath} rejects
   */
  @NotNull
  @Override
  protected String getTestDataPath() {
    try {
      return Path.of(JavaTestUtil.getJavaTestDataPath()).toRealPath().toString();
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
      assertEquals("Usage of constructor <b><code>Super.Super()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testImplicitCtrCall2() {
    try {
      doTest("Super");
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of constructor <b><code>Super.Super()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testMultipleInterfacesImplementation() {
    doTest("IFoo");
  }

  public void testMultipleInterfacesImplementationThroughCommonInterface() {
    doTest("IFoo");
  }

  public void testUsageInExtendsList() { doSingleFileTest(); }
  public void testDeepDeleteParameterSimple() { doSingleFileTest(); }
  public void testDeepDeleteParameterOtherTypeInBinaryExpression() { doSingleFileTest(); }
  public void testDeepDeleteFieldAndAssignedParameter() { doSingleFileTest(); }
  public void testImpossibleToDeepDeleteParameter() { doSingleFileTest(); }
  public void testNoDeepDeleteParameterUsedInCallQualifier() { doSingleFileTest(); }
  public void testNoDeepDeleteParameterUsedInNextArgumentExpression() { doSingleFileTest(); }
  public void testToDeepDeleteParameterOverriders() { doSingleFileTest(); }
  public void testDeleteParameterOfASiblingMethod() { doSingleFileTest(); }
  public void testDeleteMethodCascade() { doSingleFileTest(); }
  public void testDeleteMethodKeepEnumValues() { doSingleFileTest(); }
  public void testDeleteMethodCascadeRecursive() { doSingleFileTest(); }
  public void testDeleteMethodCascadeOverridden() { doSingleFileTest(); }
  public void testDeleteParameterAndUpdateJavadocRef() { doSingleFileTest(); }
  public void testDeleteConstructorParameterWithAnonymousClassUsage() { doSingleFileTest(); }
  public void testAccidentalPropertyRef() { doSingleFileTest(); }
  public void testMethodCannotBePrivate() { doSingleFileTest(); }
  public void testNoRedundantImplements() { doSingleFileTest(); }

  public void testRecordAccessorCannotBePrivate() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_16, getTestRootDisposable());
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
      assertEquals("Usage of method <b><code>Super.foo()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testParameterFromFunctionalInterface() {
    try {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of interface <b><code>SAM</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testFunctionalInterfaceMethod() {
    try {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_8);
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of interface <b><code>SAM</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testAmbiguityAfterParameterDelete() {
    try {
      doSingleFileTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>foo()</code></b> is already defined in class <b><code>Test</code></b>", e.getMessage());
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
      assertEquals("Usage of local variable <b><code>varName</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testParameterSideEffect() {
    try {
      doTest("Super");
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of parameter <b><code>i</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testUsageInGenerated() {
    doTest("A");
  }

  public void testImplicitClass() {
    myDoCompare = false;
    try {
      doTest((rootDir, rootAfter) -> {
        configureByExistingFile(rootDir.findFileByRelativePath("src/p/ImplicitClass.java"));
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

  public void testImplicitClass2() {
    myDoCompare = false;
    try {
      doTest((rootDir, rootAfter) -> {
        configureByExistingFile(rootDir.findFileByRelativePath("src/p/ImplicitClass.java"));
        performAction(Map.of());
      });
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>Inner.x()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testFileWithTwoClasses() {
    myDoCompare = false;
    try {
      doTest((rootDir, rootAfter) -> {
        configureByExistingFile(rootDir.findFileByRelativePath("src/p/TwoClasses.java"));
        SafeDeleteHandler.invoke(getProject(), new PsiElement[]{myFile}, true);
      });
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("""
                     Usage of class <b><code>p.Next</code></b> that is not safe to delete.
                     Usage of class <b><code>p.TwoClasses</code></b> that is not safe to delete.""", e.getMessage());
    }
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
      assertEquals("Usage of class <b><code>Test.Foo</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testConflictInInheritor() {
    try {
      doSingleFileTest();
      fail("Side effect was ignored");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>foo()</code></b> is already defined in class <b><code>B</code></b>", e.getMessage());
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
      try {
        doSingleFileTest(Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true));
      }
      finally {
        WriteAction.run(() -> scratchFile.delete(this));
      }
    });
  }

  public void testDeepDeleteFieldAndInitializerMethod() { doSingleFileTest(); }
  public void testDeleteMethodCascadeWithField() { doSingleFileTest(); }
  public void testForInitExpr() { doSingleFileTest(); }
  public void testForInitList() { doSingleFileTest(); }
  public void testForInitDeclaration() { doSingleFileTest(); }
  public void testForUpdateExpr() { doSingleFileTest(); }
  public void testForUpdateList() { doSingleFileTest(); }
  public void testUpdateContractOnParameterRemoval() { doSingleFileTest(); }
  public void testMultipleFieldsInSingleDeclaration1() { doSingleFileTest(); }
  public void testMultipleFieldsInSingleDeclaration2() { doSingleFileTest(); }
  public void testMultipleFieldsInSingleDeclaration3() { doSingleFileTest(); }
  public void testMultipleVariablesInSingleDeclaration1() { doSingleFileTest(); }
  public void testMultipleVariablesInSingleDeclaration2() { doSingleFileTest(); }
  public void testMultipleVariablesInSingleDeclaration3() { doSingleFileTest(); }

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

  public void testRecordComponentCompactConstructor() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_16, getTestRootDisposable());
    try {
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of record component <b><code>z</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testRecordComponentConflict() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_16, getTestRootDisposable());
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

  public void testNonAccessibleGrandParent() {
    try {
      doTest("foo.Parent");
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of class <b><code>foo.Parent</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testMethodReference1() {
    try{
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>Safe.x(int)</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testMethodReference2() {
    try{
      doSingleFileTest();
      fail();
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method reference will be converted to a lambda expression", e.getMessage());
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

  public void testMentionInCommentAndString() {
    doSingleFileTest(Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, true));
  }

  public void testMentionInCommentSearchOff() {
    doSingleFileTest();
  }

  public void testMentionInNonCodeFiles() {
    doTest("p.Gone", Map.of(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, true));
  }

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

  public void testFieldWithAccessors() {
    doSingleFileTest();
  }

  public void testSuperMethodDeleted() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, true));
  }

  public void testOverridersKept() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, false));
  }

  public void testUnusedCalleesKept() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, false));
  }

  public void testParameterInCallersKept() {
    doSingleFileTest(Map.of(ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, false));
  }

  public void testSeveralMethods() { doSingleFileTest(); }

  public void testSeveralMethodsCallingEachOther() { doSingleFileTest(); }

  public void testSeveralMethodsOneUsed() {
    try {
      doSingleFileTest();
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Usage of method <b><code>A.first()</code></b> that is not safe to delete.", e.getMessage());
    }
  }

  public void testSeveralParameters() { doSingleFileTest(); }

  public void testUnusedOverloadedConstructor() { doSingleFileTest(); }

  public void testUnusedEnumConstant() { doSingleFileTest(); }

  public void testUnusedAnnotationMethod() { doSingleFileTest(); }

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
    doSingleFileTest(Map.of());
  }

  /**
   * @param answers the answers to the questions of the deletion, on top of {@link #defaultAnswers()}
   */
  private void doSingleFileTest(@NotNull Map<ModPlanOption<?>, Object> answers) {
    try {
      configureByFile(getTestRoot() + getTestName(false) + ".java");
      performAction(answers);
      checkResultByFile(getTestRoot() + getTestName(false) + "_after.java");
    }
    catch (Exception e) {
      throw e instanceof RuntimeException r ? r : new RuntimeException(e);
    }
  }

  private void performAction(String qClassName, @NotNull Map<ModPlanOption<?>, Object> answers) {
    final PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);
    configureByExistingFile(aClass.getContainingFile().getVirtualFile());
    if (myEditor.getCaretModel().getOffset() == 0) {
      myEditor.getCaretModel().moveToOffset(aClass.getTextOffset());
    }
    performAction(answers);
  }

  private void performAction(@NotNull Map<ModPlanOption<?>, Object> testAnswers) {
    // Each caret marks an element; several carets delete their elements in one request.
    List<PsiElement> elements = new ArrayList<>();
    for (Caret caret : myEditor.getCaretModel().getAllCarets()) {
      PsiElement element = TargetElementUtil.getInstance().findTargetElement(
        myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, caret.getOffset());
      if (element != null && !elements.contains(element)) {
        elements.add(element);
      }
    }
    assertFalse("No element found in text:\n" + getFile().getText(), elements.isEmpty());
    Map<ModPlanOption<?>, Object> answers = new LinkedHashMap<>(defaultAnswers());
    answers.putAll(testAnswers);
    SafeDeleteTestSupport.NonCodeUsageRecorder nonCodeUsageRecorder = new SafeDeleteTestSupport.NonCodeUsageRecorder();
    SafeDeleteTestSupport.applyOptions(answers, nonCodeUsageRecorder, getTestRootDisposable());
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
    String pathWithoutExtension = getTestDataPath() + getTestRoot() + getTestName(false);
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
    SafeDeleteHandler.invoke(getProject(), elements.toArray(PsiElement.EMPTY_ARRAY), true);
  }
}