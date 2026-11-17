// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class OptimizeImportsTest {
  static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/optimizeImports";

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(BASE_PATH);
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    boolean preserveModuleImports = javaSettings.isPreserveModuleImports();
    boolean deleteUnusedModuleImports = javaSettings.isDeleteUnusedModuleImports();
    PackageEntryTable table = javaSettings.IMPORT_LAYOUT_TABLE;
    int classOnDemand = javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND;
    int namesOnDemand = javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND;
    Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
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

  private void doTest() {
    doTest(null);
  }

  private void doTest(@Nullable String notification) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    OptimizeImportsTestUtil.doTest(myFixture, myFixture.getTestName(false), ".java", notification);
  }

  @Test
  public void testSCR6138() { doTest(); }
  @Test
  public void testSCR18364() { doTest(); }
  @Test
  public void testStaticImports1() { doTest(); }
  @Test
  public void testStaticImportsToOptimize() { doTest(); }
  @Test
  public void testStaticImportsToOptimizeMixed() { doTest(); }
  @Test
  public void testStaticImportsToOptimize2() { doTest(); }
  @Test
  public void testStaticImportsToPreserve() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
  @Test
  public void testEmptyImportList() { doTest(); }
  @Test
  public void testIDEADEV10716() { doTest(); }
  @Test
  public void testUnresolvedImports() { doTest(); }
  @Test
  public void testUnresolvedImports2() { doTest(); }
  @Test
  public void testInterfaceMethodThroughInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public interface Foo {" +
                       "  static void foo() {}" +
                       "  interface Inner extends Foo {}" +
                       "}");
    doTest();
  }
  @Test
  public void testStringTemplates() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.STRING_TEMPLATES.getMinimumLevel(), () -> {
      myFixture.addClass("""
      package java.lang;
      
      public interface StringTemplate {
        Processor<String, RuntimeException> STR = StringTemplate::interpolate;
      }
      """);
      doTest();
    });
  }

  @Test
  public void testImplicitIoImport1() {
    implicitIoImport();
  }

  private void implicitIoImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_23_PREVIEW, () -> {
      myFixture.addClass("""
        package java.io;
        
        public final class IO {
          public static void println(Object obj) {}
        }
        """);
      doTest();
    });
  }

  @Test
  public void testImplicitIoImport2() {
    implicitIoImport();
  }

  @Test
  public void testImplicitIoImport3() {
    implicitIoImport();
  }

  @Test
  public void testImplicitModulesWithImplicitClass() {
    doTest();
  }

  @Test
  public void testConflictJavaLang(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p1; public class String {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  @Test
  public void testConflictCurrentPackage(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p2; public class Conflict {}");
    myFixture.addClass("package p1; public class Conflict {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest("Removed 5 imports, added 1 import");
  }

  @Test
  public void testConflictModuleImport(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  @Test
  public void testConflictModuleImportDoNotDeleteModule(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  @Test
  public void testConflictModuleImportImplicitClass(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  @Test
  public void testConflictModuleImportImplicitClass2(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p1; public class List {}");
    myFixture.addClass("package p1; public class A1 {}");
    myFixture.addClass("package p1; public class A2 {}");
    myFixture.addClass("package p1; public class A3 {}");
    myFixture.addClass("package p1; public class A4 {}");
    myFixture.addClass("package p1; public class A5 {}");
    doTest();
  }

  @Test
  public void testConflictModuleImportImplicitClassDemandOverModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package p1; public class List {}");
      myFixture.addClass("package p1; public class A1 {}");
      myFixture.addClass("package p1; public class A2 {}");
      myFixture.addClass("package p1; public class A3 {}");
      myFixture.addClass("package p1; public class A4 {}");
      myFixture.addClass("package p1; public class A5 {}");
      doTest();
    });
  }

  @Test
  public void testConflictModuleImportImplicitClassDemandOverModule2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package p1; public class List {}");
      myFixture.addClass("package p1; public class A1 {}");
      myFixture.addClass("package p1; public class A2 {}");
      myFixture.addClass("package p1; public class A3 {}");
      myFixture.addClass("package p1; public class A4 {}");
      myFixture.addClass("package p1; public class A5 {}");
      doTest();
    });
  }


  @Test
  public void testDontShowOnDemandIfAllSingles() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p1; public class List<T> {}");
    myFixture.addClass("package p2; public class List<T> {}");
    myFixture.addClass("package p2; public class Something {}");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = 1;
    doTest();
  }

  @Test
  public void testConflictStaticImport(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testConflictStaticImportWithImplicitClassDemandOverModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_24_PREVIEW, () -> {

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

  @Test
  public void testConflictStaticImportWithImplicitClass(){
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testNoModifications() { doTest(); }
  @Test
  public void testKeepCommentsOnImports() { doTest("Removed 3 imports"); }
  @Test
  public void testNewImportListIsEmptyAndCommentPreserved() { doTest(); }
  @Test
  public void testNewImportListIsEmptyAndJavaDocWithInvalidCodePreserved() { doTest(); }

  @Test
  public void testDontCollapseToOnDemandImport() { doTest(); }
  @Test
  public void testDontInsertRedundantJavaLangImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      package imports;
      
      public enum Values {
        String, Object, Double
      }""");
    doTest();
  }
  @Test
  public void testIgnoreInaccessible() { doTest();}

  @Test
  public void testEnsureConflictingImportsNotCollapsed() {
    doTest("Removed 1 import, added 1 import");
  }

  @Test
  public void testSameNamedImportedClasses() {
    doTest();
  }

  @Test
  public void testConflictingWithJavaLang() {
    doTest();
  }

  @Test
  public void testDoNotInsertImportForClassVisibleByInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    doTest("Rearranged imports");
  }

  @Test
  public void testConflictingOnDemandImports() {
    doTest();
  }

  @Test
  public void testExcludeNonStaticElementsFromStaticConflictingMembers() {
    doTest();
  }

  @Test
  public void testDisabledFormatting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeStyleSettings temp = CodeStyle.createTestSettings();
    NamedScopeDescriptor descriptor = new NamedScopeDescriptor("Test");
    descriptor.setPattern("file:*.java");
    temp.getExcludedFiles().addDescriptor(descriptor);
    CodeStyle.doWithTemporarySettings(myFixture.getProject(), temp, () -> doTest());
  }

  @Test
  public void testScratch() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    VirtualFile scratch =
      ScratchRootType.getInstance()
        .createScratchFile(myFixture.getProject(), PathUtil.makeFileName("scratch", "java"), JavaLanguage.INSTANCE,
                           """
                             import java.util.List;
                             import java.util.List;
                             import java.util.List;

                             class Scratch { }""", ScratchFileService.Option.create_if_missing);
    assertNotNull(scratch);
    myFixture.configureFromExistingVirtualFile(scratch);
    EdtTestUtil.runInEdtAndWait(() -> {
      runOptimizeImports();
      myFixture.checkResult("class Scratch { }");
    });
  }

  @Test
  public void testLeavesDocumentUnblocked() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", "import static java.ut<caret>il.List.*; class Foo {}");
    EdtTestUtil.runInEdtAndWait(() -> {
      runOptimizeImports();

      assertFalse(PsiDocumentManager.getInstance(myFixture.getProject()).isDocumentBlockedByPsi(myFixture.getEditor().getDocument()));

      myFixture.checkResult("class Foo {}");
    });
  }

  private void runOptimizeImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.type('A'); // make file dirty
    myFixture.type('\b');
    IntentionAction fix = PlatformTestUtil.waitForFuture(
      ReadAction.nonBlocking(() -> QuickFixFactory.getInstance().createOptimizeImportsFix(true, myFixture.getFile())).submit(
        AppExecutorUtil.getAppExecutorService())).asIntention();
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

  @Test
  public void testNoStubPsiMismatchOnRecordInsideImportList() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      import java.ut<caret>il.List;
      record foo.bar.Goo;
      import java.util.Collection;

      class Foo {}""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));

      // whatever: main thing it didn't throw
      myFixture.checkResult("""
                              record foo.bar.Goo;
                              import java.util.Collection;

                              class Foo {}""");
    });
  }

  @Test
  public void testNoStubPsiMismatchOnRecordInsideImportList2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      import java.ut<caret>il.Set;record\s
      import x java.util.Map;

      import java.util.Map;

      class Foo {}""");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));

      // whatever: main thing it didn't throw
      assertThat(myFixture.doHighlighting(HighlightSeverity.ERROR)).isNotEmpty();
    });
  }

  @Test
  public void testOptimizeImportNotOnTheFly() {
    checkOptimizeImport();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidImport() {
    checkOptimizeImport();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidImport2() {
    checkOptimizeImport();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidImportNoIntention() {
    checkOptimizeImportNoIntention();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidStaticImportPackageNoIntention() {
    checkOptimizeImportNoIntention();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidStaticImportClassResolveToLocalVariableNoIntention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject(
      "foo/Util.java",
      """
        package foo;
        
        public class Util {}
        """
    );
    checkOptimizeImportNoIntention();
  }


  @Test
  public void testOptimizeImportNotOnTheFlyInvalidStaticImportClassResolveToFieldNoIntention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject(
      "foo/Util.java",
      """
        package foo;
        
        public class Util {}
        """
    );
    checkOptimizeImportNoIntention();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidStaticImportResolveToFieldViaThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject(
      "foo/Util.java",
      """
        package foo;
        
        public class Util {}
        """
    );
    checkOptimizeImport();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidStaticImportResolveToMethodViaThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject(
      "foo/Util.java",
      """
        package foo;
        
        public class Util {}
        """
    );
    checkOptimizeImport();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidStaticImportClassResolveToParameterNoIntention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject(
      "foo/Util.java",
      """
        package foo;
        
        public class Util {}
        """
    );
    checkOptimizeImportNoIntention();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidImportOnDemand() {
    checkOptimizeImport();
  }

  @Test
  public void testOptimizeImportNotOnTheFlyInvalidImportOnDemandNoIntention() {
    checkOptimizeImportNoIntention();
  }

  private void checkOptimizeImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MissortedImportsInspection(), new UnusedImportInspection());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
      IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("optimize.imports.fix"));
      myFixture.launchAction(intention);
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  private void checkOptimizeImportNoIntention() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new MissortedImportsInspection(), new UnusedImportInspection());
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
      IntentionAction intention = myFixture.getAvailableIntention(QuickFixBundle.message("optimize.imports.fix"));
      assertNull(intention);
    });
  }

  @Test
  public void testRemovingAllUnusedImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>util.Set;
      import java.util.Map;

      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      runOptimizeImports();
      myFixture.checkResult("package p;\n\n");
    });
  }

  @Test
  public void testRemoveUnusedImportFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>util.Set;

      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));
      myFixture.checkResult("package p;\n\n");
    });
  }

  @Test
  public void testRemoveUnusedImportFixShownEvenForUnresolvedImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.configureByText("a.java", """
      package p;

      import java.<caret>blahblah.Set;

      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));
      myFixture.checkResult("package p;\n\n");
    });
  }

  @Test
  public void testRemoveUnusedImportFixMustDeleteAllUnusedImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(myFixture.findSingleIntention(JavaErrorBundle.message("remove.unused.imports.quickfix.text")));
      myFixture.checkResult("""
                              package p;

                              // just remove, not reorganize
                              import java.util.List;
                              // this for sure
                              import java.util.ArrayList;

                              class X { List a = new ArrayList(); }
                              """);
    });
  }

  @Test
  public void testPerFileImportSettings() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeStyle.dropTemporarySettings(myFixture.getProject());
    MockCodeStyleSettingsModifier modifier = new MockCodeStyleSettingsModifier(
      myFixture.getTestName(false) + ".java",
      settings -> {
        JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
        javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
        javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      });
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), CodeStyleSettingsModifier.EP_NAME, modifier, myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testOptimizeImportMessage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileName = myFixture.getTestName(false) + ".java";
    PsiFile file = myFixture.configureByFile(fileName);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
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

  @Test
  public void testImportModuleLastWithoutSpace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package bbb; public class BBB {}");
      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);

      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testImportModuleInTheMiddleWithoutSpace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package bbb; public class BBB {}");
      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();

      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "aaa", false));
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_MODULE_IMPORTS);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "bbb", false));
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);


      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testImportModuleFirstWithSpace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
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

  @Test
  public void testExtraBlankLinesBetweenImports() {
    JavaCodeStyleSettings javaSettings = setUpBlankLinesTest();
    javaSettings.setKeepBlankLinesBetweenImports(true);
    doTest();
  }

  @Test
  public void testExtraBlankLinesBetweenImportsLayoutOnly() {
    JavaCodeStyleSettings javaSettings = setUpBlankLinesTest();
    javaSettings.setKeepBlankLinesBetweenImports(false);
    doTest();
  }

  @Test
  public void testExtraBlankLinesBetweenImportsWithComment() {
    JavaCodeStyleSettings javaSettings = setUpBlankLinesTest();
    javaSettings.setKeepBlankLinesBetweenImports(false);
    // rebuilding the list would drop the standalone comment, so the extra blank lines are kept instead
    doTest();
  }

  private JavaCodeStyleSettings setUpBlankLinesTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.custom; public class MyClass1 {}");
    myFixture.addClass("package com.custom; public class MyClass2 {}");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());

    javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "javax", true));
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    return javaSettings;
  }

  @Test
  public void testIncorrectOrderWithoutModuleImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package ccc; public class CCC {}");
    JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());

    javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
    javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
    javaSettings.setPreserveModuleImports(true);
    doTest();
  }

  @Test
  public void testIncorrectOrderWithoutModuleImportConfigWithModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(new PackageEntry(false, "java", true));
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testIncorrectOrderWithoutModuleImportMixStaticAndNonStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package ccc; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());

      javaSettings.IMPORT_LAYOUT_TABLE = new PackageEntryTable();
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.BLANK_LINE_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
      javaSettings.IMPORT_LAYOUT_TABLE.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testImportModuleOverOtherImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testImportModuleConflictWithPackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      myFixture.addClass("package aaa; public class DDD {}");
      myFixture.addClass("package aaa; public class EEE {}");
      myFixture.addClass("package aaa; public class List {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testImportModuleConflictWithSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      myFixture.addClass("package aaa; public class DDD {}");
      myFixture.addClass("package aaa; public class EEE {}");
      myFixture.addClass("package aaa; public class List {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testImportModuleOnDemandConflictWithSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("package aaa; public class AAA {}");
      myFixture.addClass("package aaa; public class BBB {}");
      myFixture.addClass("package aaa; public class CCC {}");
      myFixture.addClass("package aaa; public class DDD {}");
      myFixture.addClass("package aaa; public class EEE {}");
      myFixture.addClass("package aaa; public class List {}");
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      javaSettings.setPreserveModuleImports(true);
      doTest();
    });
  }

  @Test
  public void testDoNotInsertImportForClassVisibleByInheritanceWithModuleConflictDoNotDeleteModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
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

  @Test
  public void testDoNotInsertImportForClassVisibleByInheritanceWithModuleConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
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

  @Test
  public void testNotDeleteModuleImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
    doTest();
  }

  @Test
  public void testUnresolvedReferenceAfterParenthesis() { doTest(); }
  @Test
  public void testInvalidExtendsList() { doTest(); }
  @Test
  public void testUnusedImportInBrokenAssignment() { doTest(); }
  @Test
  public void testNotCollapseIfThereIsFieldConflict() { doTest(); }
  @Test
  public void testCollapseIfThereIsMethodConflict() { doTest(); }

  @Test
  public void testNotCollapseWithImplicitConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package imports;
                         
                         public enum Values {
                             String, Object, Objects, Double, Float, Integer
                         }""");
    doTest();
  }

  @Test
  public void testCollapseWithoutConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //now it is collapsed, but it is under the question
    myFixture.addClass("""
      package imports;
      
      public enum Values {
         Object, Objects, Double, Float, Integer
      }""");
    doTest();
  }

  @Test
  public void testNotCollapseEvenWithoutConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    //this behavior is conservative
    myFixture.addClass("""
    package imports
    
    public enum Values {
        Object, Objects, Double, Float, Integer
    }""");
    doTest();
  }

  @Test
  public void testNotCollapseAccessible() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
    package imports;
    
    public class Holder {
        public static final int A = 1;
        public static final int B = 2;
        public static final int C = 3;
        public static final int Files = 999;
    }""");
    doTest();
  }

  @Test
  public void testCollapseNotAccessible() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
    package imports;
    
    public class Holder {
        public static final int A = 1;
        public static final int B = 2;
        public static final int C = 3;
        private static final int Files = 999;
    }""");
    doTest();
  }
}
