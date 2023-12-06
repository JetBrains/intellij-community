// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvHighlightingJdk6Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting6";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(
      new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection(), new JavacQuirksInspection(), new RedundantCastInspection());
    setLanguageLevel(LanguageLevel.JDK_1_6);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), getTestRootDisposable());
  }

  private void doTest(boolean checkWarnings) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  public void testJava5CastConventions() { setLanguageLevel(LanguageLevel.JDK_1_5); doTest(true); }
  public void testJavacQuirks() { doTest(true); }
  public void testMethodReturnTypeSubstitutability() { doTest(true); }
  public void testIDEADEV11877() { doTest(false); }
  public void testIDEA108285() { doTest(false); }
  public void testClassObjectAccessibility() { doTest(false); }
  public void testRedundantCastInConditionalExpression() { doTest(true); }
  public void testUnhandledExceptions() { doTest(true); }
  public void testUnhandledExceptionsValueOf() { doTest(true); }
  public void testUnsupportedFeatures() { doTest(false); }
  public void testEnumInitializers() { doTest(false); }
  public void testEnumSynthetics() { doTest(false); }
  public void testEnumWithoutConstants() { doTest(false); }
  public void testEnumInstanceFieldInitializer() { doTest(false); }
  public void testIDEA79251() { doTest(false); }
  public void testIDEA65473() { doTest(false); }
  public void testIDEA61415() { doTest(false); }
  public void testGenericArrayCreationWithGenericTypeWithOneUnboundedWildcardOneNormalParams() { doTest(false); }
  public void testAgentPremain() { doTest(false); }
  public void testInitializedBeforeUsed() { doTest(false); }
  public void testUnreachableAssignments() { doTest(false); }
  public void testCompileTypeConstantsAccessibleFromStaticFieldInitializers() { doTest(false);}
  public void testInheritUnrelatedConcreteMethodsWithSameSignature() { doTest(false);}
  public void testStatementWithExpression() { doTest(false); }
  public void testAssignmentFromStringToObject() { doTest(true); }
  public void testUnhandledErrorsFromEnumConstructors() { doTest(true);}
  public void testSkipAbstractMethodsIfTheyMustBeDeclaredInNonAbstractSuperclass() { doTest(false); }
  public void testVariableUsedBeforeAssignmentWithParenthesis() { doTest(false); }
  public void testVararg() { doTest(false); }
  public void testThisInArgListOfAnonymous() { doTest(false); }
  public void testSameFieldNameErrorOnSecond() { doTest(false); }
  public void testEnumConstantWithoutInterfaceImplementation() { doTest(false); }
  public void testAmbiguityChecksForImplicitSuperConstructorCall() { doTest(false); }
  public void testSpeculateOnUnhandledExceptionsOverResolvedConstructorOnly() { doTest(false); }
  public void testStaticOnDemandImportResolvesToClass() { doTest(false); }
  public void testReachableWhileBodyDueToConstantStringComparison() { doTest(false); }
  public void testPrivateClassReferencedInAnnotationOnSibling() { doTest(false); }
  public void testOrderAlreadyInitializedErrors() { doTest(false); }
  public void testInnerClassImportShouldNotLeadToClassUsage() { enableInspectionTool(new UnusedDeclarationInspection(true)); doTest(true); }
}