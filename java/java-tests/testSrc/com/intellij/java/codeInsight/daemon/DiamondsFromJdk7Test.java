// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Do not distribute test data which is similar to jdk's test data in our public sources.
 * <p/>
 * DO NOT MOVE TO COMMUNITY
 */
public class DiamondsFromJdk7Test extends LightDaemonAnalyzerTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/diamonds";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection(), new JavacQuirksInspection(), new RedundantCastInspection());
    setLanguageLevel(LanguageLevel.JDK_1_7);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, getModule(), getTestRootDisposable());
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have src.zip and weird method handle signatures
  }

  private void doTest(boolean checkWarnings, boolean checkInfos, InspectionProfileEntry... inspections) {
    enableInspectionTools(inspections);
    doTest( BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  public void testDiamondPos1() { doTest(false, false); }
  public void testDiamondPos2() { doTest(false, false); }
  public void testDiamondPos3() { doTest(false, false); }
  public void testDiamondPos4() { doTest(false, false); }
  public void testDiamondPos5() { doTest(false, false); }
  public void testDiamondPos6() { doTest(false, false); }
  public void testDiamondPos7() { doTest(false, false); }
  public void testDiamondNeg15() { doTest(false, false); }
  public void testDiamondPos9() { doTest(false, false); }
  public void testDiamondNeg1() { doTest(false, false); }
  public void testDiamondNeg2() { doTest(false, false); }
  public void testDiamondNeg3() { doTest(false, false); }
  public void testDiamondNeg4() { doTest(false, false); }
  public void testDiamondNeg5() { doTest(false, false); }
  public void testDiamondNeg6() { doTest(false, false); }
  public void testDiamondNeg7() { doTest(false, false); }
  public void testDiamondNeg8() { doTest(false, false); }
  public void testDiamondNeg9() { doTest(false, false); }
  public void testDiamondNeg10() { doTest(false, false); }
  public void testDiamondNeg11() { doTest(false, false); }
  public void testDiamondNeg12() { doTest(false, false); }
  public void testDiamondNeg13() { doTest(false, false); }
  public void testDiamondNeg14() { doTest(false, false); }
  public void testDiamondMisc() { doTest(false, false); }
  public void testNestedDiamond() { doTest(false, false); }
}