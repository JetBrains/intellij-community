// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;


import com.intellij.JavaTestUtil;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesFixture;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesFixture;
import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesProjectFixture;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M2;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M3;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.M4;
import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.MAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ResolveModuleImportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJava9ModulesProjectFixture();
  private static final Logger LOG = Logger.getInstance(ResolveModuleImportTest.class);
  private final TestFixture<LightJava9ModulesFixture> java9 =
    lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/importModule/resolve");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    ModuleRootModificationUtil.addModuleLibrary(myFixture.getModule(), "moduleA", getJarRootUrls("lib/moduleA-1.0.jar"),
                                                getJarRootUrls("lib/moduleA-1.0-sources.jar"));
    ModuleRootModificationUtil.addModuleLibrary(myFixture.getModule(), "moduleB", getJarRootUrls("lib/moduleB-1.0.jar"),
                                                getJarRootUrls("lib/moduleB-1.0-sources.jar"));

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

  private List<String> getJarRootUrls(String path) {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    return List.of(VfsUtil.getUrlForLibraryRoot(new File(myFixture.getTestDataPath(), path)));
  }

  @Test
  public void testResolveModuleLibrary() {
    addCode("Test.java", """
      import module my.test.moduleA;
      
      class Test {
        ModuleA <caret>module;
      }""");

    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.test.moduleA.ModuleA", psiClass.getQualifiedName());
  }

  @Test
  public void testResolveModuleLibraryInternalPackage() {
    addCode("Test.java", """
      import module my.test.moduleA;
      
      class Test {
        ModuleAInternal <caret>internal;
      }""");

    assertNull(getPsiClass());
  }

  @Test
  public void testResolveAutoModule() {
    addCode("Test.java", """
      import module my.test.moduleB;
      
      class Test {
        ModuleB <caret>module;
      }""");

    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.test.moduleB.ModuleB", psiClass.getQualifiedName());
  }

  @Test
  public void testResolveAutoModuleExternalPackage() {
    addCode("Test.java", """
      import module my.test.moduleB;
      
      class Test {
        ModuleBExternal <caret>module;
      }""");

    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.test.moduleB.external.ModuleBExternal", psiClass.getQualifiedName());
  }

  @Test
  public void testResolveSourceModule() {
    addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB;
      }
      """, M2);
    addCode("my/source/moduleB/SourceTestB.java", """
      package my.source.moduleB;
      class SourceTestB {}
      """, M2);
    addCode("Test.java", """
      import module my.source.moduleB;
      class Test {
        SourceTestB <caret>module;
      }
      """);
    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.source.moduleB.SourceTestB", psiClass.getQualifiedName());
  }

  @Test
  public void testResolveTransitiveDependency() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    Module m2 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M2.moduleName);
    Module m3 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M3.moduleName);
    ModuleRootModificationUtil.addDependency(m2, m3, DependencyScope.COMPILE, true);
    addCode("module-info.java", """
      module my.source.moduleB {
        requires transitive my.source.moduleC;
      }
      """, M2);
    addCode("module-info.java", """
      module my.source.moduleC {
        exports my.source.moduleC;
      }
      """, M3);
    addCode("my/source/moduleC/SourceTestC.java", """
      package my.source.moduleC;
      class SourceTestC {}
      """, M3);
    addCode("Test.java", """
      import module my.source.moduleB;
      class Test {
        SourceTestC <caret>module;
      }
      """);
    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.source.moduleC.SourceTestC", psiClass.getQualifiedName());
  }

  @Test
  public void testMultiResolve() {
    addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB;
      }
      """, M2);
    addCode("my/source/moduleB/MyTest.java", """
      package my.source.moduleB;
      class MyTest {}
      """, M2);

    addCode("module-info.java", """
      module my.source.moduleC {
        exports my.source.moduleC;
      }
      """, M4);
    addCode("my/source/moduleC/MyTest.java", """
      package my.source.moduleC;
      class MyTest {}
      """, M4);

    addCode("Test.java", """
      import module my.source.moduleB;
      import module my.source.moduleC;
      class Test {
        MyTest module = new <caret>MyTest();
      }
      """);
    ResolveResult[] resolveResults = getResolveResults();
    assertEquals(2, resolveResults.length);
    assertEquals(Set.of("my.source.moduleC.MyTest",
                        "my.source.moduleB.MyTest"),
                 Set.of(((PsiClass)resolveResults[0].getElement()).getQualifiedName(),
                        ((PsiClass)resolveResults[1].getElement()).getQualifiedName()));
  }

  @Test
  public void testResolveToModuleAccessed() {
    addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB to my.source.moduleA;
      }
      """, M2);
    addCode("my/source/moduleB/SourceTestB.java", """
      package my.source.moduleB;
      class SourceTestB {}
      """, M2);

    addCode("module-info.java", """
      module my.source.moduleA {
        requires my.source.moduleB;
      }
      """);
    addCode("Test.java", """
      package my;
      import module my.source.moduleB;
      class Test {
        SourceTestB <caret>module;
      }
      """);
    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.source.moduleB.SourceTestB", psiClass.getQualifiedName());
  }

  @Test
  public void testResolveToModuleNotAccessed() {
    addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB to my.source.moduleC;
      }
      """, M2);
    addCode("my/source/moduleB/SourceTestB.java", """
      package my.source.moduleB;
      class SourceTestB {}
      """, M2);

    addCode("module-info.java", """
      module my.source.moduleA {
        requires my.source.moduleB;
      }
      """);
    addCode("Test.java", """
      package my;
      import module my.source.moduleB;
      class Test {
        SourceTestB <caret>module;
      }
      """);
    PsiClass psiClass = getPsiClass();
    assertNull(psiClass);
  }

  @Test
  public void testResolveToModuleAccessedViaManifest() {
    addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB to my.source.moduleA;
      }
      """, M2);
    addCode("my/source/moduleB/SourceTestB.java", """
      package my.source.moduleB;
      class SourceTestB {}
      """, M2);

    java9.get().addResourceFile("META-INF/MANIFEST.MF", """
      Manifest-Version: 1.0
      Automatic-Module-Name: my.source.moduleA
      """, MAIN);
    addCode("Test.java", """
      package my;
      import module my.source.moduleB;
      class Test {
        SourceTestB <caret>module;
      }
      """);
    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.source.moduleB.SourceTestB", psiClass.getQualifiedName());
  }

  @Test
  public void testResolveTransitiveRecursion() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    Module m2 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M2.moduleName);
    Module m3 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName(M3.moduleName);
    ModuleRootModificationUtil.addDependency(m2, m3, DependencyScope.COMPILE, true);
    addCode("module-info.java", """
      module my.source.moduleB {
        requires transitive my.source.moduleC;
      }
      """, M2);
    addCode("my/source/moduleB/SourceTestB.java", """
      package my.source.moduleB;
      class SourceTestB {}
      """, M2);

    addCode("module-info.java", """
      module my.source.moduleC {
        exports my.source.moduleC;
        requires transitive my.source.moduleB;
      }
      """, M3);
    addCode("my/source/moduleC/SourceTestC.java", """
      package my.source.moduleC;
      class SourceTestC {}
      """, M3);

    addCode("Test.java", """
      import module my.source.moduleB;
      class Test {
        SourceTestC <caret>module;
      }
      """);
    PsiClass psiClass = getPsiClass();
    assertNotNull(psiClass);
    assertEquals("my.source.moduleC.SourceTestC", psiClass.getQualifiedName());
  }

  @Test
  public void testAmbiguousModuleImport() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

  @Test
  public void testModuleImportWithPackageImport() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
      PsiClass psiClass = getPsiClass();
      assertNotNull(psiClass);
      assertEquals("my.source.moduleA.Imported", psiClass.getQualifiedName());
    });
  }

  @Test
  public void testOptimizeImportWithModuleConflict() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), ()->{
      prepareAmbiguousModuleTests();
      String fileName = myFixture.getTestName(false) + ".java";
      PsiFile file = myFixture.configureByFile(fileName);
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
        try {
          JavaCodeStyleManager.getInstance(myFixture.getProject()).optimizeImports(file);
          PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
          PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
          myFixture.checkResultByFile(myFixture.getTestName(false) + "_after" + ".java");
          PsiTestUtil.checkFileStructure(file);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      });
    });
  }

  @Test
  public void testModuleImportWithDefaultPackageImport() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), ()->{
      addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB;
      }
      """, M2);
        addCode("my/source/moduleB/String.java", """
      package my.source.moduleB;
      public class String {}
      """, M2);
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
      PsiClass psiClass = getPsiClass();
      assertNotNull(psiClass);
      assertEquals("java.lang.String", psiClass.getQualifiedName());
    });
  }

  @Test
  public void testAmbiguousModuleImportWithPackageImport() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_23_PREVIEW, ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

    @Test
    public void testModuleImportWithSingleImport() {
      LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.checkHighlighting();
      PsiClass psiClass = getPsiClass();
      assertNotNull(psiClass);
      assertEquals("my.source.moduleA.Imported", psiClass.getQualifiedName());
    });
  }

  @Test
  public void testOptimizeImportWithSimilarNames() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    addCode("module-info.java", """
    module my.source.moduleB {
      exports my.source.moduleB;
    }
    """, M2);
    addCode("my/source/moduleB/Imported1.java", """
    package my.source.moduleB;
    public class Imported1 {}
    """, M2);
    addCode("module-info.java", """
    module my.source.moduleB1 {
      exports my.source.moduleB1;
    }
    """, M4);
    addCode("my/source/moduleB1/Imported2.java", """
    package my.source.moduleB1;
    public class Imported2 {}
    """, M4);

    String fileName = myFixture.getTestName(false) + ".java";
    PsiFile file = myFixture.configureByFile(fileName);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      try {
        JavaCodeStyleManager.getInstance(myFixture.getProject()).optimizeImports(file);
        PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
        myFixture.checkResultByFile(myFixture.getTestName(false) + "_after" + ".java");
        PsiTestUtil.checkFileStructure(file);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }

  private void prepareAmbiguousModuleTests() {
    addCode("module-info.java", """
    module my.source.moduleB {
      exports my.source.moduleB;
    }
    """, M2);
    addCode("my/source/moduleB/Imported.java", """
    package my.source.moduleB;
    public class Imported {}
    """, M2);
    addCode("my/source/moduleB/B.java", """
    package my.source.moduleB;
    public class B {}
    """, M2);
    addCode("module-info.java", """
    module my.source.moduleA {
      exports my.source.moduleA;
    }
    """, M4);
    addCode("my/source/moduleA/Imported.java", """
    package my.source.moduleA;
    public class Imported {}
    """, M4);
    addCode("my/source/moduleA/A.java", """
    package my.source.moduleA;
    public class A {}
    """, M4);
    addCode("Test.java", """
    """);
  }

  @Nullable
  private PsiClass getPsiClass() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    return ReadAction.computeBlocking(() -> {
      PsiField field = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiField.class);
      assertNotNull(field);
      return PsiUtil.resolveClassInClassTypeOnly(field.getType());
    });
  }

  private ResolveResult[] getResolveResults() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    return ReadAction.computeBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      return ((PsiPolyVariantReference)reference).multiResolve(false);
    });
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text, @NotNull ModuleDescriptor descriptor) {
    java9.get().addFile(name, text, descriptor);
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text) {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.configureByText(name, text);
  }
}
