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
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.formatting.MockCodeStyleSettingsModifier;
import com.intellij.formatting.fileSet.NamedScopeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;

public class OptimizeImportsTest extends OptimizeImportsTestCase {
  static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImports";

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
    CodeStyleSettings temp = CodeStyle.createTestSettings();
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor("Test");
    descriptor.setPattern("file:*.java");
    temp.getExcludedFiles().addDescriptor(descriptor);
    CodeStyle.doWithTemporarySettings(getProject(), temp, () -> doTest());
  }

  public void testLeavesDocumentUnblocked() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", "import static java.ut<caret>il.List.*; class Foo {}");
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));

    assertFalse(PsiDocumentManager.getInstance(getProject()).isDocumentBlockedByPsi(myFixture.getEditor().getDocument()));

    myFixture.checkResult("class Foo {}");
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  public void testNoStubPsiMismatchOnRecordInsideImportList() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", "import java.ut<caret>il.List;\n" +
                                        "record foo.bar.Goo;\n" +
                                        "import java.util.Collection;\n\n" +
                                        "class Foo {}");
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));

    // whatever: main thing it didn't throw
    myFixture.checkResult("record foo.bar.Goo;\n" +
                          "import java.util.Collection;\n\n" +
                          "class Foo {}");
  }

  public void testNoStubPsiMismatchOnRecordInsideImportList2() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", "" +
                                        "import java.ut<caret>il.Set;record \n" +
                                        "import x java.util.Map;\n\n" +
                                        "import java.util.Map;\n\n" +
                                        "class Foo {}");
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));

    // whatever: main thing it didn't throw
    myFixture.checkResult("record\n" +
                          "java.util.Map;\n" +
                          "\n" +
                          "class Foo {}");
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
