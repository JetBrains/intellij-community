// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.imports;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;

import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*;

public class UnusedImportGlobalWithModulesInspectionTest extends LightJava9ModulesCodeInsightFixtureTestCase {


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Module main = ModuleManager.getInstance(getProject()).findModuleByName(MAIN.moduleName);

    Module m2 = ModuleManager.getInstance(getProject()).findModuleByName(M2.moduleName);

    Module m3 = ModuleManager.getInstance(getProject()).findModuleByName(M3.moduleName);

    Module m4 = ModuleManager.getInstance(getProject()).findModuleByName(M4.moduleName);
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
    boolean deleteUnusedModuleImports = javaSettings.isDeleteUnusedModuleImports();
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        javaSettings.setDeleteUnusedModuleImports(deleteUnusedModuleImports);
      }
    });
    javaSettings.setDeleteUnusedModuleImports(false);
  }

  public void testParentNotUsedChildUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        addCode("my/source/moduleC/Sql.java", """
          package my.source.moduleC;
          public class Sql {}
          """, M4);
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleD;/*unused*/
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
                new Connection();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleD;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
                new Connection();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
             }
           }
          """);
      }
    );
  }

  public void testNoOneUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
             }
           }
          """);
      }
    );
  }

  public void testNoOneUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
        doTest("""
          import module my.source.moduleB;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
             }
           }
          """);
      }
    );
  }

  public void testParentNotUsedChildNotUsed() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          public final class Main {
             public static void main(String[] args) {
             }
           }
          """);
      }
    );
  }

  public void testParentNotUsedChildNotUsedDeleteUnused() {
    IdeaTestUtil.withLevel(
      getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
        JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(true);
        doTest("""
          import module my.source.moduleB;/*unused*/
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          import my.source.moduleB.*;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          import my.source.moduleB.*;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          import my.source.moduleC.*;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          import my.source.moduleC.*;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;
          
          import my.source.moduleB.*;/*unused*/
          import my.source.moduleC.*;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
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
        doTest("""
          import module my.source.moduleB;
          import module my.source.moduleC;/*unused*/
          import module my.source.moduleE;/*unused*/
          
          import my.source.moduleB.*;/*unused*/
          import my.source.moduleC.*;/*unused*/
          
          public final class Main {
             public static void main(String[] args) {
                new Sql();
                new Transaction();
             }
           }
          """);
      }
    );
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/java/java-tests/testData/ig/com/siyeh/igtest/imports/globalInspectionWithModules";
  }

  private void doTest(@Language("JAVA") @NotNull String classText) {
    myFixture.addClass(classText);
    doTest();
  }

  private void doTest() {
    GlobalInspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new UnusedImportInspection());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(getTestDataPath(), getTestName(false)).getPath());
  }


  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text, @NotNull ModuleDescriptor descriptor) {
    addFile(name, text, descriptor);
  }
}
