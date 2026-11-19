// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.imports;


import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesFixture;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.GlobalInspectionContextForTests;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import java.io.File;
import java.util.Collections;
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
public class UnusedImportGlobalWithModulesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJava9ModulesProjectFixture();
  private final TestFixture<LightJava9ModulesFixture> java9 =
    lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/imports/globalInspectionWithModules");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    Module main = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(MAIN.moduleName);

    Module m2 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M2.moduleName);

    Module m3 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M3.moduleName);

    Module m4 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M4.moduleName);
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
    boolean deleteUnusedModuleImports = javaSettings.isDeleteUnusedModuleImports();
    Disposer.register(myFixture.getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        javaSettings.setDeleteUnusedModuleImports(deleteUnusedModuleImports);
      }
    });
    javaSettings.setDeleteUnusedModuleImports(false);
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

  @Test
  public void testNoOneUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
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

  @Test
  public void testNoOneUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
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

  @Test
  public void testParentNotUsedChildNotUsed() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(false);
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

  @Test
  public void testParentNotUsedChildNotUsedDeleteUnused() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(
      myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
        JavaCodeStyleSettings.getInstance(myFixture.getProject()).setDeleteUnusedModuleImports(true);
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

  private void doTest(@Language("JAVA") @NotNull String classText) {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.addClass(classText);
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    GlobalInspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new UnusedImportInspection());
    AnalysisScope scope = new AnalysisScope(myFixture.getProject());
    GlobalInspectionContextForTests globalContext =
      InspectionsKt.createGlobalContextForTool(scope, myFixture.getProject(), Collections.<InspectionToolWrapper<?, ?>>singletonList(toolWrapper));

    EdtTestUtil.runInEdtAndWait(() -> {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext);
      InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(myFixture.getTestDataPath(), myFixture.getTestName(false)).getPath());
    });
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text, @NotNull ModuleDescriptor descriptor) {
    java9.get().addFile(name, text, descriptor);
  }
}
