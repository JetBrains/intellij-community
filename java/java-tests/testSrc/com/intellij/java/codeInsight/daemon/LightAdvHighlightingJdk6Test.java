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
    enableInspectionTools(new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection(), new JavacQuirksInspection(), new RedundantCastInspection());
    setLanguageLevel(LanguageLevel.JDK_1_6);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), getTestRootDisposable());
  }
  
  private void doTest(boolean checkWarnings, boolean checkInfos) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  public void testJava5CastConventions() { setLanguageLevel(LanguageLevel.JDK_1_5); doTest(true, false); }
  public void testJavacQuirks() { doTest(true, false); }
  public void testMethodReturnTypeSubstitutability() { doTest(true, false); }
  public void testIDEADEV11877() { doTest(false, false); }
  public void testIDEA108285() { doTest(false, false); }
  public void testClassObjectAccessibility() { doTest(false, false); }
  public void testRedundantCastInConditionalExpression() { doTest(true, false); }
  public void testUnhandledExceptions() { doTest(true, false); }
  public void testUnhandledExceptionsValueOf() { doTest(true, false); }
  public void testUnsupportedFeatures() { doTest(false, false); }
  public void testEnumInitializers() { doTest(false, false); }
  public void testEnumSynthetics() { doTest(false, false); }
  public void testEnumWithoutConstants() { doTest(false, false); }
  public void testEnumInstanceFieldInitializer() { doTest(false, false); }
  public void testIDEA79251() { doTest(false, false); }
  public void testIDEA65473() { doTest(false, false); }
  public void testIDEA61415() { doTest(false, false); }
  public void testGenericArrayCreationWithGenericTypeWithOneUnboundedWildcardOneNormalParams() { doTest(false, false); }
  public void testAgentPremain() { doTest(false, false); }
  public void testInitializedBeforeUsed() { doTest(false, false); }
  public void testUnreachableAssignments() { doTest(false, false); }
  public void testCompileTypeConstantsAccessibleFromStaticFieldInitializers() { doTest(false, false);}
  public void testInheritUnrelatedConcreteMethodsWithSameSignature() { doTest(false, false);}
  public void testStatementWithExpression() { doTest(false, false); }

  public void testAssignmentFromStringToObject() {
    doTest(true, false);
  }

  public void testUnhandledErrorsFromEnumConstructors() {
    doTest(true, false);
  }
  public void testSkipAbstractMethodsIfTheyMustBeDeclaredInNonAbstractSuperclass() {
    doTest(false, false);
  }
  public void testVariableUsedBeforeAssignmentWithParenthesis() {
    doTest(false, false);
  }
  public void testThisInArgListOfAnonymous() {
    doTest(false, false);
  }
  public void testSameFieldNameErrorOnSecond() {
    doTest(false, false);
  }

  public void testEnumConstantWithoutInterfaceImplementation() {
    doTest(false, false);
  }

  public void testAmbiguityChecksForImplicitSuperConstructorCall() {
    doTest(false, false);
  }

  public void testSpeculateOnUnhandledExceptionsOverResolvedConstructorOnly() {
    doTest(false, false);
  }

  public void testStaticOnDemandImportResolvesToClass() {
    doTest(false, false);
  }

  public void testReachableWhileBodyDueToConstantStringComparison() {
    doTest(false, false);
  }

  public void testPrivateClassReferencedInAnnotationOnSibling() {
    doTest(false, false);
  }
}
