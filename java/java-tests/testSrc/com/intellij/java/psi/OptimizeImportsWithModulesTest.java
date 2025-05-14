// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.WriteCommandAction;
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
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*;

public class OptimizeImportsWithModulesTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImportsWithModules";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    com.intellij.openapi.module.Module main = ModuleManager.getInstance(getProject()).findModuleByName(MAIN.moduleName);

    com.intellij.openapi.module.Module m2 = ModuleManager.getInstance(getProject()).findModuleByName(M2.moduleName);

    com.intellij.openapi.module.Module m3 = ModuleManager.getInstance(getProject()).findModuleByName(M3.moduleName);

    com.intellij.openapi.module.Module m4 = ModuleManager.getInstance(getProject()).findModuleByName(M4.moduleName);
    Module m5 = ModuleManager.getInstance(getProject()).findModuleByName(M5.moduleName);
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
    javaSettings.setDeleteUnusedModuleImports(false);
    myFixture.enableInspections(new UnusedImportInspection());
  }


  public void testParentNotUsedChildUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleC/Sql.java", """
          package my.source.moduleC;
          public class Sql {}
          """, M4);
        doTest();
      }
    );
  }


  public void testParentNotUsedChildUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleC/Sql.java", """
          package my.source.moduleC;
          public class Sql {}
          """, M4);
        doTest();
      }
    );
  }


  public void testParentUsedChildNotUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }


  public void testParentUsedChildNotUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }


  public void testParentUsedChildUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
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


  public void testParentUsedChildUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
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


  public void testParentUsedChildUsedGrandChildUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
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

  public void testParentUsedChildUsedGrandChildUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
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

  public void testOneUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }

  public void testOneUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
        addCode("my/source/moduleB/Sql.java", """
          package my.source.moduleB;
          public class Sql {}
          """, M2);
        doTest();
      }
    );
  }

  public void testNoOneUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        doTest();
      }
    );
  }

  public void testNoOneUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
        doTest();
      }
    );
  }

  public void testParentNotUsedChildNotUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        doTest();
      }
    );
  }

  public void testParentUsedChildUsedParentCovered() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
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


  public void testParentUsedChildUsedParentCoveredDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
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

  public void testParentUsedChildUsedChildCovered() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
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

  public void testParentUsedChildUsedChildCoveredDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
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

  public void testParentUsedChildUsedChildCoveredParentCovered() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
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

  public void testParentUsedChildUsedChildCoveredParentCoveredDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
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
    String fileName = getTestName(false) + extension;
    PsiFile file = myFixture.configureByFile(fileName);
    myFixture.testHighlighting(true , false, false);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myFixture.checkResultByFile(getTestName(false) + "_after" + extension);
        PsiTestUtil.checkFileStructure(file);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text, @NotNull MultiModuleJava9ProjectDescriptor.ModuleDescriptor descriptor) {
    addFile(name, text, descriptor);
  }
}
