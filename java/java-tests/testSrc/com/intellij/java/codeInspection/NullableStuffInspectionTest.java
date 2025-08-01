// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.intellij.java.codeInspection.DataFlowInspectionTest.addJavaxNullabilityAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionTestCase.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionTestCase.setupTypeUseAnnotations;

public class NullableStuffInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  private NullableStuffInspection myInspection = new NullableStuffInspection();

  private final GeneratedSourcesFilter myGeneratedSourcesFilter = new GeneratedSourcesFilter() {
    @Override
    public boolean isGeneratedSource(@NotNull VirtualFile file, @NotNull Project project) {
      return file.getName().startsWith("Gen");
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/nullableProblems/";
  }

  private void doTest() {
    myFixture.enableInspections(myInspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  private void doTestWithFix(String intentionAction) {
    doTest();
    myFixture.launchAction(myFixture.findSingleIntention(intentionAction));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
    ExtensionTestUtil
      .maskExtensions(GeneratedSourcesFilter.EP_NAME, Collections.singletonList(myGeneratedSourcesFilter), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    myInspection = null;
    super.tearDown();
  }

  public void testProblems() { doTest();}

  public void testAnnotatingPrimitivesTypeUse() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testTypeParameterShouldNotWarn() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testAnnotatingPrimitivesAmbiguous() {
    DataFlowInspectionTestCase.setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  public void testAnnotatingArrayAmbiguous() {
    DataFlowInspectionTestCase.setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  public void testProblems2() { doTest(); }
  public void testNullableFieldNotnullParam() { doTest(); }
  public void testNotNullFieldNullableParam() { doTest(); }
  public void testNotNullCustomException() { doTest(); }

  public void testNotNullAnnotationChecksInChildClassMethods() { doTest(); }

  public void testGetterSetterProblems() { doTest(); }
  public void testNonTrivialGettersSetters() { doTest(); }
  public void testGetterSetterFieldMismatch() { doTest(); }
  public void testAbstractMapAndSortedMap() { doTest(); }

  public void testOverriddenMethods() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  public void testNoOverridingChecksOnInapplicableAnnotations() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  public void testOverriddenMethodsWithDefaults() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  public void testOverriddenMethodsInGeneratedCode() {
    Registry.get("idea.report.nullity.missing.in.generated.overriders").setValue(false, getTestRootDisposable());
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    myFixture.addClass("package foo; public class GenMyTestClass implements MyTestClass { String implementMe() {} }");
    doTest();
  }

  public void testOverriddenViaMethodReference() { doTest(); }
  public void testMethodReferenceListOf() { doTest(); }
  public void testOverridingExternalNotNull() { doTest(); }

  public void testIgnoreExternalNotNull() {
    myInspection.IGNORE_EXTERNAL_SUPER_NOTNULL = true;
    doTest();
  }

  public void testNotNullParameterOverridesNotAnnotated() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }

  public void testNotNullByDefaultParameterOverridesNotAnnotated() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testNullableCalledWithNullUnderNotNullByDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testNullableSiblingOverriding() { doTest(); }

  public void testNonAnnotatedSiblingOverriding() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }

  public void testHonorSuperParameterDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.addClass("package foo; import javax.annotation.*; public interface NullableFunction { void fun(@Nullable Object o); }");
    myFixture.addClass("package foo; public interface AnyFunction { void fun(Object o); }");

    doTest();
  }

  public void testHonorThisParameterDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHonorCustomDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault({PARAMETER, FIELD, METHOD, LOCAL_VARIABLE}) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface NotNullByDefault {}");

    myFixture.addFileToProject("foo/package-info.java", "@NotNullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testOverrideCustomDefault() {
    addJavaxNullabilityAnnotations(myFixture);
    myFixture.addClass("package custom;" +
                       "public @interface CheckForNull {}");

    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setNullables("custom.CheckForNull");
    Disposer.register(myFixture.getTestRootDisposable(), nnnManager::setNullables);

    myFixture.addClass("package foo;" +
                       "import static java.lang.annotation.ElementType.*;" +
                       "@javax.annotation.meta.TypeQualifierDefault(METHOD) " +
                       "@javax.annotation.Nonnull " +
                       "public @interface ReturnValuesAreNonnullByDefault {}");

    myFixture.addFileToProject("foo/package-info.java", "@ReturnValuesAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testNullPassedToNotNullParameter() {
    doTest();
  }

  public void testNullPassedToNotNullConstructorParameter() {
    doTest();
  }

  public void testNullPassedAsPartNotNullAnnotatedOfVarArg() {
    doTest();
  }

  public void testHonorParameterDefaultInSetters() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testNullableDefaultOnClassVsNonnullOnPackage() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@NonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testDefaultOverridesExplicit() {
    addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testBeanValidationNotNull() {
    myFixture.addClass("package javax.annotation.constraints; public @interface NotNull{}");
    DataFlowInspectionTestCase.setCustomAnnotations(getProject(), getTestRootDisposable(), "javax.annotation.constraints.NotNull", "javax.annotation.constraints.Nullable");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  public void testForeachParameterNullability() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testPassingNullableCollectionWhereNotNullIsExpected() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testPassingNullableMapWhereNotNullIsExpected() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testPassingNullableMapValueWhereNotNullIsExpected() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testOverridingNotNullCollectionWithNullable() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testNotNullCollectionItemWithNullableSuperType() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testNotNullTypeArgumentWithNullableSuperType() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testNullableTypeArgumentSOE() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testOverrideGenericMethod() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testTypeUseNotNullOverriding() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testAnnotateOverridingParametersOnNotNullMethod() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Annotate overriding method parameters");
  }

  public void testRemoveMethodAnnotationRemovesOverriders() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Remove annotation");
  }

  public void testRemoveParameterAnnotationRemovesOverriders() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Remove annotation");
  }

  public void testNullPassedToNullableParameter() {
    doTest();
  }

  public void testTypeUseArrayAnnotation() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testIncorrectPlacement() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testInheritTypeUse() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testMismatchOnArrayElementTypeUse() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testInheritAmbiguous() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    DataFlowInspectionTestCase.setupAmbiguousAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testIncorrectPlacementAmbiguous() {
    DataFlowInspectionTestCase.setupAmbiguousAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testIncorrectPlacementAmbiguousJava6() {
    DataFlowInspectionTestCase.setupAmbiguousAnnotations("typeUse", myFixture);
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  public void testOverridersHaveNonDefaultAnnotation() {
    myFixture.addClass("package org.eclipse.jdt.annotation;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.PARAMETER) public @interface NonNull { }");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Annotate overriding method parameters");
  }

  public void testQuickFixOnTypeArgument() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTestWithFix("Annotate as '@NotNull'");
  }

  public void testRemoveAnnotationWithImportQuickFix() {
    doTestWithFix("Remove annotation");
  }

  public void testQuickFixOnTypeArgumentNullable() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTestWithFix("Annotate as '@NotNull'");
  }

  public void testCheckerDefaultTypeUseRecursiveGeneric() {
    DataFlowInspectionTestCase.addCheckerAnnotations(myFixture);
    doTest();
  }

  public void testMapComputeLambdaAnnotation() {
    setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testDisableOnLocals() {
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  public void testDisableOnLocals2() {
    myInspection.REPORT_NULLABILITY_ANNOTATION_ON_LOCALS = false;
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  public void testOverriddenWithNullMarked() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  public void testNullableParameterOverride() {
    doTest();
  }

  public void testDisableOnClass() {
    doTest();
  }

  public void testParameterUnderDefaultNotNull() {
    doTest();
  }
  
  public void testRedundantNotNull() {
    doTest();
  }
  
  public void testRedundantNotNull2() {
    doTest();
  }
  
  public void testNoNotNullWarningIfIndirectSuperMethodIsAnnotated() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }
  
  public void testIncompatibleConstructors() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
    IntentionAction action = myFixture.findSingleIntention("Fix all '@NotNull/@Nullable problems' problems in file");
    myFixture.launchAction(action);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }
  
  public void testIncompatibleInstantiation() {
    addJSpecifyNullMarked(myFixture);
    addJavaxNullabilityAnnotations(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
  
  public void testNullableExtendsNullable() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
}