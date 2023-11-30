// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.excludedFiles.NamedScopeDescriptor;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.formatting.MockCodeStyleSettingsModifier;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.lang.java.JavaImportOptimizer;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class OptimizeImportsTest extends OptimizeImportsTestCase {
  static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImports";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnusedDeclarationInspection());
  }

  public void testSCR6138() { doTest(); }
  public void testSCR18364() { doTest(); }
  public void testStaticImports1() { doTest(); }
  public void testStaticImportsToOptimize() { doTest(); }
  public void testStaticImportsToOptimizeMixed() { doTest(); }
  public void testStaticImportsToOptimize2() { doTest(); }
  public void testStaticImportsToPreserve() {
    myFixture.addClass("""
                         package pack.sample;

                         public interface Sample {
                             String Foo = "FOO";
                             enum Type {
                                 T
                             }
                         }
                         """);
    doTest();
  }
  public void testEmptyImportList() { doTest(); }
  public void testIDEADEV10716() { doTest(); }
  public void testUnresolvedImports() { doTest(); }
  public void testUnresolvedImports2() { doTest(); }
  public void testInterfaceMethodThroughInheritance() {
    myFixture.addClass("package foo; public interface Foo {" +
                       "  static void foo() {}" +
                       "  interface Inner extends Foo {}" +
                       "}");
    doTest();
  }
  public void testStringTemplates() {
    doTest();
  }
  public void testNewImportListIsEmptyAndCommentPreserved() { doTest(); }
  public void testNewImportListIsEmptyAndJavaDocWithInvalidCodePreserved() { doTest(); }

  public void testDontCollapseToOnDemandImport() { doTest(); }
  public void testDontInsertRedundantJavaLangImports() {
    myFixture.addClass("""
      package imports;
      
      public enum Values {
        String, Object, Double
      }""");
    doTest();
  }
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

  public void testDoNotInsertImportForClassVisibleByInheritance() {
    myFixture.addClass("""
                         package one;
                         public interface Super {
                           class Result {}
                           
                           Result x();
                         }
                         """);
    myFixture.addClass("""
                         package two;
                         public class Result {}
                         public class One {}
                         public class Two {}
                         public class Three {}
                         public class Four {}
                         public class Five {}
                         """);
    myFixture.addClass("""
                         package three;
                         public class Result {}
                         public class Six {}
                         public class Seven {}
                         public class Eight {}
                         public class Nine {}
                         public class Ten {}
                         """);
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

  public void testScratch() {
    myFixture.enableInspections(new UnusedImportInspection());
    VirtualFile scratch =
      ScratchRootType.getInstance()
        .createScratchFile(getProject(), PathUtil.makeFileName("scratch", "java"), JavaLanguage.INSTANCE,
                           """
                             import java.util.List;
                             import java.util.List;
                             import java.util.List;

                             class Scratch { }""", ScratchFileService.Option.create_if_missing);
    assertNotNull(scratch);
    myFixture.configureFromExistingVirtualFile(scratch);
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));
    myFixture.checkResult("class Scratch { }");
  }

  public void testLeavesDocumentUnblocked() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", "import static java.ut<caret>il.List.*; class Foo {}");
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));

    assertFalse(PsiDocumentManager.getInstance(getProject()).isDocumentBlockedByPsi(myFixture.getEditor().getDocument()));

    myFixture.checkResult("class Foo {}");
  }

  public void testNoStubPsiMismatchOnRecordInsideImportList() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      import java.ut<caret>il.List;
      record foo.bar.Goo;
      import java.util.Collection;

      class Foo {}""");
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));

    // whatever: main thing it didn't throw
    myFixture.checkResult("""
                            record foo.bar.Goo;
                            import java.util.Collection;

                            class Foo {}""");
  }

  public void testNoStubPsiMismatchOnRecordInsideImportList2() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      import java.ut<caret>il.Set;record\s
      import x java.util.Map;

      import java.util.Map;

      class Foo {}""");
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));

    // whatever: main thing it didn't throw
    myFixture.checkResult("class Foo {}");
  }

  public void testRemovingAllUnusedImports() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>util.Set;
      import java.util.Map;

      """);
    myFixture.launchAction(myFixture.findSingleIntention("Optimize imports"));
    myFixture.checkResult("package p;\n\n");
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

  public void testOptimizeImportMessage() {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String fileName = getTestName(false) + ".java";
      PsiFile file = myFixture.configureByFile(fileName);
      Set<ImportOptimizer> optimizers = LanguageImportStatements.INSTANCE.forFile(file);
      for (ImportOptimizer optimizer : optimizers) {
        if (optimizer instanceof JavaImportOptimizer) {
          Runnable runnable = optimizer.processFile(file);
          assertTrue(runnable instanceof ImportOptimizer.CollectingInfoRunnable);
          runnable.run();
          ImportOptimizer.CollectingInfoRunnable infoRunnable = (ImportOptimizer.CollectingInfoRunnable)runnable;
          String info = infoRunnable.getUserNotificationInfo();
          assertEquals("Removed 2 imports", info);
        }
      }
    });
  }

  private void doTest() {
    doTest(".java");
  }
}
