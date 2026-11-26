// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class LightAdvHighlightingJdk6Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting6";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(
      new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection(), new JavacQuirksInspection(), new RedundantCastInspection());
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_6);
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, myFixture.getModule(), myFixture.getTestRootDisposable());
  }

  private void doTest(boolean checkWarnings) {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, false);
  }

  @Test public void testJava5CastConventions() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_5); doTest(true); }
  @Test public void testJavacQuirks() { doTest(true); }
  @Test public void testMethodReturnTypeSubstitutability() { doTest(true); }
  @Test public void testIDEADEV11877() { doTest(false); }
  @Test public void testIDEA108285() { doTest(false); }
  @Test public void testClassObjectAccessibility() { doTest(false); }
  @Test public void testRedundantCastInConditionalExpression() { doTest(true); }
  @Test public void testUnhandledExceptions() { doTest(true); }
  @Test public void testUnhandledExceptionsValueOf() { doTest(true); }
  @Test public void testUnsupportedFeatures() { doTest(false); }
  @Test public void testEnumInitializers() { doTest(false); }
  @Test public void testEnumSynthetics() { doTest(false); }
  @Test public void testEnumWithoutConstants() { doTest(false); }
  @Test public void testEnumInstanceFieldInitializer() { doTest(false); }
  @Test public void testIDEA79251() { doTest(false); }
  @Test public void testIDEA65473() { doTest(false); }
  @Test public void testIDEA61415() { doTest(false); }
  @Test public void testGenericArrayCreationWithGenericTypeWithOneUnboundedWildcardOneNormalParams() { doTest(false); }
  @Test public void testAgentPremain() { doTest(false); }
  @Test public void testInitializedBeforeUsed() { doTest(false); }
  @Test public void testUnreachableAssignments() { doTest(false); }
  @Test public void testCompileTypeConstantsAccessibleFromStaticFieldInitializers() { doTest(false);}
  @Test public void testInheritUnrelatedConcreteMethodsWithSameSignature() { doTest(false);}
  @Test public void testStatementWithExpression() { doTest(false); }
  @Test public void testAssignmentFromStringToObject() { doTest(true); }
  @Test public void testUnhandledErrorsFromEnumConstructors() { doTest(true);}
  @Test public void testSkipAbstractMethodsIfTheyMustBeDeclaredInNonAbstractSuperclass() { doTest(false); }
  @Test public void testVariableUsedBeforeAssignmentWithParenthesis() { doTest(false); }
  @Test public void testVararg() { doTest(false); }
  @Test public void testThisInArgListOfAnonymous() { doTest(false); }
  @Test public void testSameFieldNameErrorOnSecond() { doTest(false); }
  @Test public void testEnumConstantWithoutInterfaceImplementation() { doTest(false); }
  @Test public void testAmbiguityChecksForImplicitSuperConstructorCall() { doTest(false); }
  @Test public void testSpeculateOnUnhandledExceptionsOverResolvedConstructorOnly() { doTest(false); }
  @Test public void testStaticOnDemandImportResolvesToClass() { doTest(false); }
  @Test public void testReachableWhileBodyDueToConstantStringComparison() { doTest(false); }
  @Test public void testPrivateClassReferencedInAnnotationOnSibling() { doTest(false); }
  @Test public void testOrderAlreadyInitializedErrors() { doTest(false); }
  @Test
  public void testInnerClassImportShouldNotLeadToClassUsage() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection(true));
    doTest(true);
  }
}