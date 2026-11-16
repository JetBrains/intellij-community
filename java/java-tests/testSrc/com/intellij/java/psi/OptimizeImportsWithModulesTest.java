// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesFixture;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntryTable;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesFixture;
import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesProjectFixture;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M3;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M5;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN;

@TestApplication
public class OptimizeImportsWithModulesTest {
  static final String BASE_PATH = JavaTestUtil.getJavaTestDataPath() + "/psi/optimizeImportsWithModules";
  private static final TestFixture<LightJavaProject> projectFixture = lightJava9ModulesProjectFixture();
  private static final Logger LOG = Logger.getInstance(OptimizeImportsWithModulesTest.class);
  private final TestFixture<LightJava9ModulesFixture> java9 = lightJava9ModulesFixture(projectFixture, BASE_PATH);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    com.intellij.openapi.module.Module main = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(MAIN.moduleName);

    com.intellij.openapi.module.Module m2 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M2.moduleName);

    com.intellij.openapi.module.Module m3 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M3.moduleName);

    com.intellij.openapi.module.Module m4 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M4.moduleName);
    Module m5 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M5.moduleName);
    //m2(B) -> m4(C) -> m5(D)
    //m3(E)
    ModuleRootModificationUtil.addDependency(m2, m4, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(m4, m5, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(m4, m5, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(main, m3, DependencyScope.COMPILE, true);

    addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB;
        requires transitive my.source.moduleC;
      }
      """, M2);

    addCode("module-info.java", """
      module my.source.moduleE {
        exports my.source.moduleE;
      }
      """, M3);

    addCode("module-info.java", """
      module my.source.moduleC {
        exports my.source.moduleC;
        requires transitive my.source.moduleD;
      }
      """, M4);

    addCode("module-info.java", """
      module my.source.moduleD {
        exports my.source.moduleD;
      }
      """, M5);

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
    javaSettings.setDeleteUnusedModuleImports(false);
    myFixture.enableInspections(new UnusedImportInspection());
  }

  @Test
  public void testHierarchy() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleD/Sql.java", """
          package my.source.moduleD;
          public class Sql {}
          """, M5);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      });
  }

  @Test
  public void testHierarchy2() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleD/Sql.java", """
          package my.source.moduleD;
          public class Sql {}
          """, M5);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      });
  }

  @Test
  public void testParentNotUsedChildUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleC/Sql.java", """
          package my.source.moduleC;
          public class Sql {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentNotUsedChildUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleC/Sql.java", """
          package my.source.moduleC;
          public class Sql {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildNotUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildNotUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedGrandChildUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        addCode("my/source/moduleD/Connection.java", """
          package my.source.moduleD;
          public class Connection {}
          """, M5);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedGrandChildUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        addCode("my/source/moduleD/Connection.java", """
          package my.source.moduleD;
          public class Connection {}
          """, M5);
        doTest();
      }
    );
  }

  @Test
  public void testOneUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }

  @Test
  public void testOneUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }

  @Test
  public void testNoOneUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        doTest();
      }
    );
  }

  @Test
  public void testNoOneUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        doTest();
      }
    );
  }

  @Test
  public void testParentNotUsedChildNotUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedParentCovered() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedParentCoveredDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedChildCovered() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedChildCoveredDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedChildCoveredParentCovered() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  @Test
  public void testParentUsedChildUsedChildCoveredParentCoveredDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        addCode("my/source/moduleC/Transaction.java", """
          package my.source.moduleC;
          public class Transaction {}
          """, M4);
        doTest();
      }
    );
  }

  private void doTest() {
    doTest(".java");
  }

  protected void doTest(@NotNull String extension) {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    String fileName = myFixture.getTestName(false) + extension;
    PsiFile file = myFixture.configureByFile(fileName);
    myFixture.testHighlighting(true , false, false);

    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      try {
        JavaCodeStyleManager.getInstance(myFixture.getProject()).optimizeImports(file);
        PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        myFixture.checkResultByFile(myFixture.getTestName(false) + "_after" + extension);
        PsiTestUtil.checkFileStructure(file);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text, @NotNull MultiModuleJava9ProjectDescriptor.ModuleDescriptor descriptor) {
    java9.get().addFile(name, text, descriptor);
  }
}
