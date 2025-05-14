// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.codeStyle.excludedFiles.NamedScopeDescriptor;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.MissortedImportsInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.formatting.MockCodeStyleSettingsModifier;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.java.JavaImportOptimizer;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ExecutionException;

public class OptimizeImportsTest extends OptimizeImportsTestCase {
  static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImports";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    boolean preserveModuleImports = javaSettings.isPreserveModuleImports();
    boolean deleteUnusedModuleImports = javaSettings.isDeleteUnusedModuleImports();
    PackageEntryTable table = javaSettings.IMPORT_LAYOUT_TABLE;
    int classOnDemand = javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    int namesOnDemand = javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        javaSettings.setDeleteUnusedModuleImports(deleteUnusedModuleImports);
        javaSettings.setPreserveModuleImports(preserveModuleImports);
        javaSettings.IMPORT_LAYOUT_TABLE = table;
        javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = classOnDemand;
        javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = namesOnDemand;
      }
    });
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
    IdeaTestUtil.withLevel(getModule(), JavaFeature.STRING_TEMPLATES.getMinimumLevel(), () -> {
      myFixture.addClass("""
      package java.lang;
      
      public interface StringTemplate {
        Processor<String, RuntimeException> STR = StringTemplate::interpolate;
      }
      """);
      doTest();
    });
  }

  public void testImplicitIoImport1() {
    implicitIoImport();
  }

  private void implicitIoImport() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_IMPORT_IN_IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.addClass("""
        package java.io;
        
        public final class IO {
          public static void println(Object obj) {}
        }
        """);
      doTest();
    });
  }

  public void testImplicitIoImport2() {
    implicitIoImport();
  }
  
  public void testImplicitIoImport3() {
    implicitIoImport();
  }

  public void testImplicitModulesWithImplicitClass() {
    doTest();
  }

  public void testConflictJavaLang(){
    myFixture.addClass("package p1; public class String {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  public void testConflictCurrentPackage(){
    myFixture.addClass("package p2; public class Conflict {}");
    myFixture.addClass("package p1; public class Conflict {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  public void testConflictModuleImport(){
    JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  public void testConflictModuleImportDoNotDeleteModule(){
    JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  public void testConflictModuleImportImplicitClass(){
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  public void testConflictModuleImportImplicitClass2(){
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  public void testConflictModuleImportImplicitClassDemandOverModule() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package p1; public class List {}");
      myFixture.addClass("package p1; public class A1 {}");
      myFixture.addClass("package p1; public class A2 {}");
      myFixture.addClass("package p1; public class A3 {}");
      myFixture.addClass("package p1; public class A4 {}");
      myFixture.addClass("package p1; public class A5 {}");
      doTest();
    });
  }

  public void testConflictModuleImportImplicitClassDemandOverModule2() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package p1; public class List {}");
      myFixture.addClass("package p1; public class A1 {}");
      myFixture.addClass("package p1; public class A2 {}");
      myFixture.addClass("package p1; public class A3 {}");
      myFixture.addClass("package p1; public class A4 {}");
      myFixture.addClass("package p1; public class A5 {}");
      doTest();
    });
  }


  public void testDontShowOnDemandIfAllSingles() {
    myFixture.addClass("package p1; public class List<T> {}");
    myFixture.addClass("package p2; public class List<T> {}");
    myFixture.addClass("package p2; public class Something {}");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    doTest();
  }

  public void testConflictStaticImport(){
    myFixture.addClass(
      """
        package p1;
        public class A1 {
          public static void print(Object obj) {}
          public static void foo() {}
          public static void foo1() {}
          public static void foo2() {}
          public static void foo3() {}
          public static void foo4() {}
          public static void foo5() {}
        }
        """);
    myFixture.addClass("""
        package java.io;
        
        public final class IO {
          public static void print(Object obj) {}
        }
        """);
    doTest();
  }

  public void testConflictStaticImportWithImplicitClassDemandOverModule() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {

      myFixture.addClass(
        """
          package p1;
          public class A1 {
            public static void print(Object obj) {}
            public static void foo() {}
            public static void foo1() {}
            public static void foo2() {}
            public static void foo3() {}
            public static void foo4() {}
            public static void foo5() {}
          }
          """);
      myFixture.addClass("""
                         package java.io;
                         
                         public final class IO {
                           public static void print(Object obj) {}
                         }
                         """);
      doTest();

    });
  }

  public void testConflictStaticImportWithImplicitClass(){
    myFixture.addClass(
      """
        package p1;
        public class A1 {
          public static void print(Object obj) {}
          public static void foo() {}
          public static void foo1() {}
          public static void foo2() {}
          public static void foo3() {}
          public static void foo4() {}
          public static void foo5() {}
        }
        """);
    myFixture.addClass("""
        package java.io;
        
        public final class IO {
          public static void print(Object obj) {}
        }
        """);
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

  public void testScratch() throws Exception {
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
    runOptimizeImports();
    myFixture.checkResult("class Scratch { }");
  }

  public void testLeavesDocumentUnblocked() throws Exception {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", "import static java.ut<caret>il.List.*; class Foo {}");
    runOptimizeImports();

    assertFalse(PsiDocumentManager.getInstance(getProject()).isDocumentBlockedByPsi(myFixture.getEditor().getDocument()));

    myFixture.checkResult("class Foo {}");
  }

  private void runOptimizeImports() throws ExecutionException, InterruptedException {
    myFixture.type('A'); // make file dirty
    myFixture.type('\b');
    IntentionAction fix = ReadAction.nonBlocking(() -> QuickFixFactory.getInstance().createOptimizeImportsFix(true, myFixture.getFile())).submit(
      AppExecutorUtil.getAppExecutorService()).get().asIntention();
    myFixture.doHighlighting(); // wait until highlighting is finished to .isAvailable() return true
    boolean old = CodeInsightWorkspaceSettings.getInstance(myFixture.getProject()).isOptimizeImportsOnTheFly();
    CodeInsightWorkspaceSettings.getInstance(myFixture.getProject()).setOptimizeImportsOnTheFly(true);
    try {
      myFixture.launchAction(fix);
    }
    finally {
      CodeInsightWorkspaceSettings.getInstance(myFixture.getProject()).setOptimizeImportsOnTheFly(old);
    }
  }

  public void testNoStubPsiMismatchOnRecordInsideImportList() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      import java.ut<caret>il.List;
      record foo.bar.Goo;
      import java.util.Collection;

      class Foo {}""");
    myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));

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
    myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));

    // whatever: main thing it didn't throw
    assertNotEmpty(myFixture.doHighlighting(HighlightSeverity.ERROR));
  }

  public void testOptimizeImportNotOnTheFly() {
    checkOptimizeImport();
  }

  public void testOptimizeImportNotOnTheFlyInvalidImport() {
    checkOptimizeImport();
  }

  public void testOptimizeImportNotOnTheFlyInvalidImport2() {
    checkOptimizeImport();
  }

  public void testOptimizeImportNotOnTheFlyInvalidImportNoIntention() {
    checkOptimizeImportNoIntention();
  }

  public void testOptimizeImportNotOnTheFlyInvalidImportOnDemand() {
    checkOptimizeImport();
  }

  public void testOptimizeImportNotOnTheFlyInvalidImportOnDemandNoIntention() {
    checkOptimizeImportNoIntention();
  }

  private void checkOptimizeImport() {
    myFixture.enableInspections(new MissortedImportsInspection(), new UnusedImportInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("optimize.imports.fix"));
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void checkOptimizeImportNoIntention() {
    myFixture.enableInspections(new MissortedImportsInspection(), new UnusedImportInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
    IntentionAction intention = myFixture.getAvailableIntention(QuickFixBundle.message("optimize.imports.fix"));
    assertNull(intention);
  }

  public void testRemovingAllUnusedImports() throws Exception {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>util.Set;
      import java.util.Map;

      """);
    runOptimizeImports();
    myFixture.checkResult("package p;\n\n");
  }
  public void testRemoveUnusedImportFix() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>util.Set;

      """);
    myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));
    myFixture.checkResult("package p;\n\n");
  }
  public void testRemoveUnusedImportFixShownEvenForUnresolvedImport() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>blahblah.Set;

      """);
    myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));
    myFixture.checkResult("package p;\n\n");
  }
  public void testRemoveUnusedImportFixMustDeleteAllUnusedImports() {
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      // just remove, not reorganize
      import java.util.List;
      import java.util.HashSet;
      // this for sure
      import java.<caret>util.Set;
      import java.util.ArrayList;
      import java.util.HashMap;
      
      class X { List a = new ArrayList(); }
      """);
    myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));
    myFixture.checkResult("""
                            package p;

                            // just remove, not reorganize
                            import java.util.List;
                            // this for sure
                            import java.util.ArrayList;

                            class X { List a = new ArrayList(); }
                            """);
  }

  public void testPerFileImportSettings() {
    CodeStyle.dropTemporarySettings(getProject());
    MockCodeStyleSettingsModifier modifier = new MockCodeStyleSettingsModifier(
      getTestName(false) + ".java",
      settings -> {
        JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
        javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
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


  public void testImportModuleLastWithoutSpace() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {

      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package bbb; public class BBB {}");
      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);

      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testImportModuleInTheMiddleWithoutSpace() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {

      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package bbb; public class BBB {}");
      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();

      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "aaa", false));
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "bbb", false));
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);


      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testImportModuleFirstWithSpace() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
      javaSettings.setPreserveModuleImports(true);

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);

      doTest();
    });
  }

  public void testIncorrectOrderWithoutModuleImport() {

    myFixture.addClass("package ccc; public class CCC {}");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

    javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
    javaSettings.setPreserveModuleImports(true);
    doTest();
  }

  public void testIncorrectOrderWithoutModuleImportConfigWithModule() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {

      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testIncorrectOrderWithoutModuleImportMixStaticAndNonStatic() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {

      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testImportModuleOverOtherImports() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testImportModuleConflictWithPackage() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      myFixture.addClass("package aaa; public class DDD {}");
      myFixture.addClass("package aaa; public class EEE {}");
      myFixture.addClass("package aaa; public class List {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testImportModuleConflictWithSamePackage() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      myFixture.addClass("package aaa; public class DDD {}");
      myFixture.addClass("package aaa; public class EEE {}");
      myFixture.addClass("package aaa; public class List {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  public void testImportModuleOnDemandConflictWithSamePackage() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      myFixture.addClass("package aaa; public class DDD {}");
      myFixture.addClass("package aaa; public class EEE {}");
      myFixture.addClass("package aaa; public class List {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }


  public void testDoNotInsertImportForClassVisibleByInheritanceWithModuleConflictDoNotDeleteModule() {
    JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
    myFixture.addClass("""
                         package one;
                         public interface Super {
                           class List {}
                         
                           List x();
                         }
                         """);
    myFixture.addClass("""
                         package two;
                         public class List {}
                         public class One {}
                         public class Two {}
                         public class Three {}
                         public class Four {}
                         public class Five {}
                         """);
    myFixture.addClass("""
                         package three;
                         public class List {}
                         public class Six {}
                         public class Seven {}
                         public class Eight {}
                         public class Nine {}
                         public class Ten {}
                         """);
    doTest();
  }

  public void testDoNotInsertImportForClassVisibleByInheritanceWithModuleConflict() {
    JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
    myFixture.addClass("""
                         package one;
                         public interface Super {
                           class List {}
                         
                           List x();
                         }
                         """);
    myFixture.addClass("""
                         package two;
                         public class List {}
                         public class One {}
                         public class Two {}
                         public class Three {}
                         public class Four {}
                         public class Five {}
                         """);
    myFixture.addClass("""
                         package three;
                         public class List {}
                         public class Six {}
                         public class Seven {}
                         public class Eight {}
                         public class Nine {}
                         public class Ten {}
                         """);
    doTest();
  }

  public void testNotDeleteModuleImport() {
    JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
    doTest();
  }

  public void testUnresolvedReferenceAfterParenthesis() {
    doTest();
  }

  public void testInvalidExtendsList() { doTest(); }
  
  private void doTest() {
    doTest(".java");
  }
}
