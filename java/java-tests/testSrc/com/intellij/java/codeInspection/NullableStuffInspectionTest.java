// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
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
    return JAVA_11_ANNOTATED;
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
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testTypeParameterShouldNotWarn() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testAnnotatingPrimitivesAmbiguous() {
    DataFlowInspection8Test.setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  public void testAnnotatingArrayAmbiguous() {
    DataFlowInspection8Test.setupAmbiguousAnnotations("withTypeUse", myFixture);
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
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
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
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testNullableSiblingOverriding() { doTest(); }

  public void testNonAnnotatedSiblingOverriding() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    doTest();
  }

  public void testHonorSuperParameterDefault() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.addClass("package foo; import javax.annotation.*; public interface NullableFunction { void fun(@Nullable Object o); }");
    myFixture.addClass("package foo; public interface AnyFunction { void fun(Object o); }");

    doTest();
  }

  public void testHonorThisParameterDefault() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHonorCustomDefault() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
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
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
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
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@javax.annotation.ParametersAreNonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testNullableDefaultOnClassVsNonnullOnPackage() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    myFixture.addFileToProject("foo/package-info.java", "@NonnullByDefault package foo;");

    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".java", "foo/Classes.java"));
    myFixture.enableInspections(myInspection);
    myFixture.checkHighlighting(true, false, true);
  }

  public void testBeanValidationNotNull() {
    myFixture.addClass("package javax.annotation.constraints; public @interface NotNull{}");
    DataFlowInspection8Test.setCustomAnnotations(getProject(), getTestRootDisposable(), "javax.annotation.constraints.NotNull", "javax.annotation.constraints.Nullable");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTest();
  }

  public void testForeachParameterNullability() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testPassingNullableCollectionWhereNotNullIsExpected() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testPassingNullableMapWhereNotNullIsExpected() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testPassingNullableMapValueWhereNotNullIsExpected() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testOverridingNotNullCollectionWithNullable() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testNotNullCollectionItemWithNullableSuperType() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testNotNullTypeArgumentWithNullableSuperType() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testNullableTypeArgumentSOE() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  
  public void testOverrideGenericMethod() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testTypeUseNotNullOverriding() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testAnnotateQuickFixOnMethodReference() {
    doTestWithFix("Annotate");
  }

  public void testAnnotateOverridingParametersOnNotNullMethod() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Annotate overridden method parameters");
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
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  
  public void testIncorrectPlacement() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  
  public void testInheritTypeUse() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testInheritAmbiguous() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    DataFlowInspection8Test.setupAmbiguousAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testIncorrectPlacementAmbiguous() {
    DataFlowInspection8Test.setupAmbiguousAnnotations("typeUse", myFixture);
    doTest();
  }
  
  public void testIncorrectPlacementAmbiguousJava6() {
    DataFlowInspection8Test.setupAmbiguousAnnotations("typeUse", myFixture);
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_6, this::doTest);
  }

  public void testOverridersHaveNonDefaultAnnotation() {
    myFixture.addClass("package org.eclipse.jdt.annotation;\n\nimport java.lang.annotation.*;\n\n@Target(ElementType.PARAMETER) public @interface NonNull { }");
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
    doTestWithFix("Annotate overridden method parameters");
  }

  public void testQuickFixOnTypeArgument() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    String oldDefault = manager.getDefaultNotNull();
    try {
      manager.setDefaultNotNull("typeUse.NotNull");
      doTestWithFix("Annotate as @NotNull");
    }
    finally {
      manager.setDefaultNotNull(oldDefault);
    }
  }

  public void testQuickFixOnTypeArgumentNullable() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    NullableNotNullManager manager = NullableNotNullManager.getInstance(getProject());
    String oldDefault = manager.getDefaultNotNull();
    try {
      manager.setDefaultNotNull("typeUse.NotNull");
      doTestWithFix("Annotate as @NotNull");
    }
    finally {
      manager.setDefaultNotNull(oldDefault);
    }
  }

  public void testCheckerDefaultTypeUseRecursiveGeneric() {
    myFixture.addClass("package org.checkerframework.checker.nullness.qual;import java.lang.annotation.*;" +
                       "@Target(ElementType.TYPE_USE)public @interface NonNull {}");
    myFixture.addClass("package org.checkerframework.checker.nullness.qual;import java.lang.annotation.*;" +
                       "@Target(ElementType.TYPE_USE)public @interface Nullable {}");
    myFixture.addClass("package org.checkerframework.framework.qual;" +
                       "import java.lang.annotation.*;" +
                       "enum TypeUseLocation {ALL}" +
                       "public @interface DefaultQualifier {" +
                       "  Class<? extends Annotation> value();" +
                       "  TypeUseLocation[] locations() default {TypeUseLocation.ALL};" +
                       "}");
    doTest();
  }
}