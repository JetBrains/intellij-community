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
package com.intellij.java.psi;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.MockCodeStyleSettingsModifier;
import com.intellij.formatting.fileSet.NamedScopeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.testFramework.ServiceContainerUtil;

public class OptimizeImportsTest extends OptimizeImportsTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImports";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  public void testSCR6138() { doTest(); }
  public void testSCR18364() { doTest(); }
  public void testStaticImports1() { doTest(); }
  public void testStaticImportsToOptimize() { doTest(); }
  public void testStaticImportsToOptimizeMixed() { doTest(); }
  public void testStaticImportsToOptimize2() { doTest(); }
  public void testEmptyImportList() { doTest(); }
  public void testIDEADEV10716() { doTest(); }
  public void testUnresolvedImports() { doTest(); }
  public void testUnresolvedImports2() { doTest(); }
  public void testNewImportListIsEmptyAndCommentPreserved() { doTest(); }
  public void testNewImportListIsEmptyAndJavaDocWithInvalidCodePreserved() { doTest(); }

  public void testDontCollapseToOnDemandImport() { doTest(); }
  public void testIgnoreInaccessible() { doTest();}

  public void testEnsureConflictingImportsNotCollapsed() {
    doTest();
  }

  public void testSameNamedImportedClasses() {
    doTest();
  }

  public void testConflictingWithJavaLang() {
    doTest();
  }

  public void testConflictingOnDemandImports() {
    doTest();
  }

  public void testExcludeNonStaticElementsFromStaticConflictingMembers() {
    doTest();
  }

  public void testDisabledFormatting() {
    CodeStyleSettings temp = new CodeStyleSettings();
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor("Test");
    descriptor.setPattern("file:*.java");
    temp.getExcludedFiles().addDescriptor(descriptor);
    CodeStyle.doWithTemporarySettings(getProject(), temp, () -> doTest());
  }

  public void testPerFileImportSettings() {
    CodeStyle.dropTemporarySettings(getProject());
    MockCodeStyleSettingsModifier modifier = new MockCodeStyleSettingsModifier(
      getTestName(false) + ".java",
      settings -> {
        JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
        javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      });
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), CodeStyleSettingsModifier.EP_NAME, modifier, getTestRootDisposable());
    doTest();
  }

  private void doTest() {
    doTest(".java");
  }
}
