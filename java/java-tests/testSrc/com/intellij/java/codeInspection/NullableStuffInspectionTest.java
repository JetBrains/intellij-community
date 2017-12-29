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

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class NullableStuffInspectionTest extends LightCodeInsightFixtureTestCase {
  private static final DefaultLightProjectDescriptor PROJECT_DESCRIPTOR = new DefaultLightProjectDescriptor() {
    @Override
    public Sdk getSdk() {
      return PsiTestUtil.addJdkAnnotations(IdeaTestUtil.getMockJdk18());
    }
  };
  private NullableStuffInspection myInspection = new NullableStuffInspection();

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return PROJECT_DESCRIPTOR;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/nullableProblems/";
  }

  private void doTest() {
    myFixture.enableInspections(myInspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = false;
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

  public void testAnnotatingPrimitivesAmbiguous() {
    DataFlowInspection8Test.setupAmbiguousAnnotations("withTypeUse", myFixture);
    doTest();
  }

  public void testProblems2() { doTest(); }
  public void testNullableFieldNotnullParam() { doTest(); }
  public void testNotNullFieldNullableParam() { doTest(); }
  public void testNotNullCustomException() { doTest(); }

  public void testNotNullFieldNotInitialized() { doTest(); }
  public void testNotNullFieldInitializedInLambda() { doTest(); }
  public void testNotNullFieldNotInitializedInOneConstructor() { doTest(); }
  public void testNotNullFieldNotInitializedSetting() {
    myInspection.REQUIRE_NOTNULL_FIELDS_INITIALIZED = false;
    doTest();
  }

  public void testNotNullByDefaultFieldNotInitialized() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    doTest();
  }

  public void testNotNullAnnotationChecksInChildClassMethods() { doTest(); }

  public void testGetterSetterProblems() { doTest(); }
  public void testNonTrivialGettersSetters() { doTest(); }
  
  public void testOverriddenMethods() {
    myInspection.REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
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

  public void testTypeUseNotNullField() {
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testTypeUseNotNullOverriding() {
    myInspection.REPORT_NOTNULL_PARAMETERS_OVERRIDES_NOT_ANNOTATED = true;
    DataFlowInspection8Test.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }

  public void testAnnotateQuickFixOnMethodReference() {
    doTest();
    myFixture.launchAction(myFixture.findSingleIntention("Annotate"));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

}