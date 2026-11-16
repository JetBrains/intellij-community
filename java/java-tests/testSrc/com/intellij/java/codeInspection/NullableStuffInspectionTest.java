// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static com.intellij.java.codeInspection.DataFlowInspectionTest.addJavaxNullabilityAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNonNull;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.setupTypeUseAnnotations;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class NullableStuffInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private NullableStuffInspection myInspection = new NullableStuffInspection();

  private final GeneratedSourcesFilter myGeneratedSourcesFilter = new GeneratedSourcesFilter() {
    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
      return file.getName().startsWith("Gen");
    }
  };

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(myInspection);
    myFixture.testHighlighting(true, false, true, myFixture.getTestName(false) + ".java");
  }

  /**
   * Checks the highlighting of a test data file whose tooltips link to the file by its path.
   * The file holds {@code $TEMP_DIR$} in place of the path of the temp directory.
   */
  private void doTestWithTempDirPaths() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileName = myFixture.getTestName(false) + ".java";
    String text = FileUtil.loadFile(new File(myFixture.getTestDataPath(), fileName));
    myFixture.configureByText(fileName, text.replace("$TEMP_DIR$", FileUtil.toSystemIndependentName(myFixture.getTempDirPath())));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  private void doTestWithFix(String intentionAction) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest();
    IntentionAction action = myFixture.findSingleIntention(intentionAction);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/nullableProblems/");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
    ExtensionTestUtil
      .maskExtensions(GeneratedSourcesFilter.EP_NAME, Collections.singletonList(myGeneratedSourcesFilter), myFixture.getTestRootDisposable());
  }

  @AfterEach
  void tearDown() {
    myInspection = null;
  }

  @Test
  public void testProblems() { doTest();}

  @Test
  public void testAnnotatingPrimitivesTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testTypeParameterShouldNotWarn() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testAnnotatingPrimitivesAmbiguous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  @Test
  public void testAnnotatingArrayAmbiguous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  @Test
  public void testProblems2() { doTest(); }
  @Test
  public void testNullableFieldNotnullParam() { doTest(); }
  @Test
  public void testNotNullFieldNullableParam() { doTest(); }
  @Test
  public void testNotNullCustomException() { doTest(); }

  @Test
  public void testNotNullAnnotationChecksInChildClassMethods() { doTest(); }

  @Test
  public void testGetterSetterProblems() { doTest(); }
  @Test
  public void testNonTrivialGettersSetters() { doTest(); }
  @Test
  public void testGetterSetterFieldMismatch() { doTest(); }
  @Test
  public void testAbstractMapAndSortedMap() { doTest(); }

  @Test
  public void testOverriddenMethods() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  @Test
  public void testStaticMethodHiding() { doTest(); }

  @Test
  public void testNoOverridingChecksOnInapplicableAnnotations() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  @Test
  public void testOverriddenMethodsWithDefaults() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  @Test
  public void testOverriddenMethodsInGeneratedCode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Registry.get("idea.report.nullity.missing.in.generated.overriders").setValue(false, myFixture.getTestRootDisposable());
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    myFixture.addClass("package foo; public class GenMyTestClass implements MyTestClass { String implementMe() {} }");
    doTest();
  }

  @Test
  public void testOverriddenViaMethodReference() { doTest(); }
  @Test
  public void testMethodReferenceListOf() { doTest(); }
  @Test
  public void testOverridingExternalNotNull() { doTest(); }

  @Test
  public void testIgnoreExternalNotNull() {
    myInspection.IGNORE_EXTERNAL_SUPER_NOTNULL = true;
    doTest();
  }

  @Test
  public void testNotNullParameterOverridesNotAnnotated() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }

  @Test
  public void testNullMarkedParameterOverridesNotAnnotated() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNotNullByDefaultParameterOverridesNotAnnotated() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  @Test
  public void testNotNullByDefaultParameterOverridesNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  @Test
  public void testNullableSiblingOverriding() { doTest(); }

  @Test
  public void testNonAnnotatedSiblingOverriding() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }

  @Test
  public void testHonorSuperParameterDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.addClass("package foo; import javax.annotation.*; public interface NullableFunction { void fun(@Nullable Object o); }");
    myFixture.addClass("package foo; public interface AnyFunction { void fun(Object o); }");

    doTest();
  }

  @Test
  public void testHonorThisParameterDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testHonorCustomDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, FIELD, METHOD, LOCAL_VARIABLE}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NotNullByDefault {}");

    myFixture.addFileToProject("foo/package-info.java", "@NotNullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testOverrideCustomDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package custom;" +
                       "public @interface CheckForNull {}");

    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(myFixture.getProject());
    nnnManager.setNullables("custom.CheckForNull");
    Disposer.register(myFixture.getTestRootDisposable(), nnnManager::setNullables);

    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault(METHOD) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface ReturnValuesAreNonnullByDefault {}");

    myFixture.addFileToProject("foo/package-info.java", "@ReturnValuesAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testHonorParameterDefaultInSetters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testNullableDefaultOnClassVsNonnullOnPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@NonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  @Test
  public void testDefaultOverridesExplicit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  @Test
  public void testBeanValidationNotNull() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package javax.annotation.constraints; public @interface NotNull{}");
    DataFlowInspectionFixture.setCustomAnnotations(myFixture.getProject(), myFixture.getTestRootDisposable(), "javax.annotation.constraints.NotNull", "javax.annotation.constraints.Nullable");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  @Test
  public void testForeachParameterNullability() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testPassingNullableCollectionWhereNotNullIsExpected() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testPassingNullableMapWhereNotNullIsExpected() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testPassingNullableMapValueWhereNotNullIsExpected() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testOverridingNotNullCollectionWithNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testNotNullCollectionItemWithNullableSuperType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testNotNullTypeArgumentWithNullableSuperType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_ANNOTATED_INSTANTIATION_NOT_NULL_TYPE = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testUnannotatedInstantiationOfNonNullTypeParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testUnannotatedInstantiationOfNonNullTypeParameterOn() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_ANNOTATED_INSTANTIATION_NOT_NULL_TYPE = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNullableTypeArgumentSOE() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testOverrideGenericMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testTypeUseNotNullOverriding() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testAnnotateOverridingParametersOnNotNullMethod() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Annotate overriding method parameters");
  }

  @Test
  public void testRemoveMethodAnnotationRemovesOverriders() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Remove annotation");
  }

  @Test
  public void testRemoveParameterAnnotationRemovesOverriders() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Remove annotation");
  }

  @Test
  public void testNullabilityAnnotationInPattern() { doTest(); }

  @Test
  public void testRemovePatternNullabilityAnnotation() { doTestWithFix("Remove annotation"); }

  @Test
  public void testTypeUseArrayAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testIncorrectPlacement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testInheritTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testMismatchOnArrayElementTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testInheritAmbiguous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    DataFlowInspectionFixture.setupAmbiguousAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testIncorrectPlacementAmbiguous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.setupAmbiguousAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testIncorrectPlacementAmbiguousJava6() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.setupAmbiguousAnnotations("typeUse", myFixture);
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  @Test
  public void testOverridersHaveNonDefaultAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package org.eclipse.jdt.annotation;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.PARAMETER) public @interface NonNull { }");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Annotate overriding method parameters");
  }

  @Test
  public void testQuickFixOnTypeArgument() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_ANNOTATED_INSTANTIATION_NOT_NULL_TYPE = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTestWithFix("Annotate as '@NotNull'");
  }

  @Test
  public void testRemoveAnnotationWithImportQuickFix() {
    doTestWithFix("Remove annotation");
  }

  @Test
  public void testQuickFixOnTypeArgumentNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTestWithFix("Annotate as '@NotNull'");
  }

  @Test
  public void testCheckerDefaultTypeUseRecursiveGeneric() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.addCheckerAnnotations(myFixture);
    doTest();
  }

  @Test
  public void testMapComputeLambdaAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  @Test
  public void testDisableOnLocals() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  @Test
  public void testDisableOnLocals2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NULLABILITY_ANNOTATION_ON_LOCALS = false;
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  @Test
  public void testOverriddenWithNullMarked() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifyUnboundedWildcardReturnOverride() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  @Test
  public void testNullableParameterOverride() {
    doTest();
  }

  @Test
  public void testDisableOnClass() {
    doTest();
  }

  @Test
  public void testRedundantNotNull() {
    doTest();
  }

  @Test
  public void testRedundantNotNullDisabled() {
    boolean previousValue = myInspection.REPORT_REDUNDANT_NULLABILITY_ANNOTATION_IN_THE_SCOPE_OF_ANNOTATED_CONTAINER;
    try {
      myInspection.REPORT_REDUNDANT_NULLABILITY_ANNOTATION_IN_THE_SCOPE_OF_ANNOTATED_CONTAINER = false;
      doTest();
    }
    finally {
      myInspection.REPORT_REDUNDANT_NULLABILITY_ANNOTATION_IN_THE_SCOPE_OF_ANNOTATED_CONTAINER = previousValue;
    }
  }

  @Test
  public void testRedundantNotNull2() {
    doTest();
  }
  
  @Test
  public void testNoNotNullWarningIfIndirectSuperMethodIsAnnotated() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }
  
  @Test
  public void testIncompatibleConstructors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
    IntentionAction action = myFixture.findSingleIntention("Fix all 'Nullability problems' problems in file");
    myFixture.launchAction(action);
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
  }
  
  @Test
  public void testIncompatibleInstantiation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    addJavaxNullabilityAnnotations(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  @Test
  public void testStreamFilterPatching() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testStreamFilterPatchingInFunction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNullableExtendsNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testIncompatibleContainer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    addJSpecifyNonNull(myFixture);
    doTest();
  }

  @Test
  public void testReturnIncompatibilitiesWithGeneric() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTestWithTempDirPaths();
  }

  @Test
  public void testAssignmentIncompatibilitiesWithGeneric() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNewExpressionBoundExtends() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNewExpressionBoundContainment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNewExpressionBoundTransitive() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    addNullnessUnspecified();
    doTest();
  }

  @Test
  public void testReferenceBoundPositions() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNewExpressionBoundSuperVsExtends() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testReturnDerivedWithNullableBound() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testNewExpressionOuterBoundSuperVsExtends() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testArrayUnderContainerAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_ANNOTATED_INSTANTIATION_NOT_NULL_TYPE = true;
    addJSpecifyNullMarked(myFixture);
    doTest();
  }
  
  @Test
  public void testDefaultNotNullTypeParameterOverrides() {
    doTest();
  }
  
  @Test
  public void testNotNullTypeParameterMethodArgument() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }

  @Test
  public void testCallIncompatibilitiesWithGeneric() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testConstructorCallIncompatibilitiesWithGeneric() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testCallIncompatibilitiesWithGenericWithNotNullToNull() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = false;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifySameInstanceGenericSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifySameInstanceGenericInheritedBound() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifyWildcardBoundTypeVariable() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTestWithTempDirPaths();
  }

  @Test
  public void testJSpecifyNullableParameterOverridesNotNull() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NULLABLE_PARAMETER_OVERRIDES_NOTNULL = true;
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    addNullnessUnspecified();
    doTest();
  }

  @Test
  public void testJSpecifyNullableParameterOverridesNotNullOptionOff() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NULLABLE_PARAMETER_OVERRIDES_NOTNULL = false;
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifyTypeParameterBoundOverridesNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //without it, conflicts in the overridden return type (NOT_NULL_TO_NULL) are not reported at all
    myInspection.REPORT_NOT_NULL_TO_NULLABLE_CONFLICTS_IN_ASSIGNMENTS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    addNullnessUnspecified();
    doTest();
  }

  @Test
  public void testJSpecifyNullableBoundInstantiatedWithNotNull() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    PsiClass superClass = myFixture.addClass("""
                                               import org.jspecify.annotations.NullMarked;
                                               import org.jspecify.annotations.Nullable;

                                               @NullMarked
                                               public class SupWithNullableBound<T extends @Nullable Object> {
                                                 public void test(T arg) {}
                                               }""");
    doTest();
    // The bound has to be read from the stub, where the extends-list reference is rebuilt in a DummyHolder and the
    // @Nullable annotation reports no owner. .
    assertFalse(((PsiFileImpl)superClass.getContainingFile()).isContentsLoaded());
  }

  @Test
  public void testJSpecifyUnspecifiedTypeArgumentInstantiatedWithNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    addNullnessUnspecified();
    doTest();
  }

  @Test
  public void testJSpecifyUnboundedWildcardFromUnmarkedScope() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifyCyclicTypeParameterBounds() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  private void addNullnessUnspecified() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package org.jspecify.annotations;
                         import java.lang.annotation.*;
                         
                         @Target(ElementType.TYPE_USE) public @interface NullnessUnspecified { }""");
  }
  
  @Test
  public void testNoOverrideWarningOnParameterization() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  @Test
  public void testJSpecifyNullMarkedDiamond() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifyStreamConcatOfNullable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }

  @Test
  public void testJSpecifyNotNullAndPlainUsage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
}
