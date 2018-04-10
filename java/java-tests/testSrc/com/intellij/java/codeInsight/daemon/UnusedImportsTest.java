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

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.psi.PsiFile;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;

public class UnusedImportsTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedImports";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedImportInspection());
  }

  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }

  public void testWithHighlightingOff() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final PsiFile file = getFile();
    final HighlightingSettingsPerFile settingsPerFile = HighlightingSettingsPerFile.getInstance(myProject);
    final FileHighlightingSetting oldSetting = settingsPerFile.getHighlightingSettingForRoot(file);
    try {
      settingsPerFile.setHighlightingSettingForRoot(file, FileHighlightingSetting.NONE);
      doDoTest(true, false, false);
    }
    finally {
      settingsPerFile.setHighlightingSettingForRoot(file, oldSetting);
    }
  }

  public void testUnclosed() throws Exception { doTest(); }

  public void testQualified() throws Exception { doTest(); }

  public void testInnersOnDemand1() throws Exception { doTest(); }
  public void testInnersOnDemand2() throws Exception { doTest(); }
  public void testStaticImportingInner() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", BASE_PATH, true, false);
  }

  public void testImportFromSamePackage1() throws Exception {
    doTest(BASE_PATH+"/package1/a.java", BASE_PATH,true,false);
  }
  public void testImportFromSamePackage2() throws Exception {
    doTest(BASE_PATH+"/package1/b.java", BASE_PATH,true,false);
  }

  protected void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}