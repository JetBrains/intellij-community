// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.JavaTestUtil;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Set;

import static com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor.*;

public class ResolveModuleImportTest extends LightJava9ModulesCodeInsightFixtureTestCase {

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/importModule/resolve";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.addModuleLibrary(getModule(), "moduleA", getJarRootUrls("lib/moduleA-1.0.jar"),
                                                getJarRootUrls("lib/moduleA-1.0-sources.jar"));
    ModuleRootModificationUtil.addModuleLibrary(getModule(), "moduleB", getJarRootUrls("lib/moduleB-1.0.jar"),
                                                getJarRootUrls("lib/moduleB-1.0-sources.jar"));

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

  private List<String> getJarRootUrls(String path) {
    return List.of(VfsUtil.getUrlForLibraryRoot(new File(getTestDataPath(), path)));
  }


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

  public void testResolveModuleLibraryInternalPackage() {
    addCode("Test.java", """
      import module my.test.moduleA;
      
      class Test {
        ModuleAInternal <caret>internal;
      }""");

    assertNull(getPsiClass());
  }

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

  public void testResolveTransitiveDependency() {
    Module m2 = ModuleManager.getInstance(getProject()).findModuleByName(M2.moduleName);
    Module m3 = ModuleManager.getInstance(getProject()).findModuleByName(M3.moduleName);
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

    addResourceFile("META-INF/MANIFEST.MF", """
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

  public void testResolveTransitiveRecursion() {
    Module m2 = ModuleManager.getInstance(getProject()).findModuleByName(M2.moduleName);
    Module m3 = ModuleManager.getInstance(getProject()).findModuleByName(M3.moduleName);
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

  public void testAmbiguousModuleImport() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

  public void testModuleImportWithPackageImport() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
      PsiClass psiClass = getPsiClass();
      assertNotNull(psiClass);
      assertEquals("my.source.moduleA.Imported", psiClass.getQualifiedName());
    });
  }

  public void testOptimizeImportWithModuleConflict() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), ()->{
      prepareAmbiguousModuleTests();
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        String fileName = getTestName(false) + ".java";
        try {
          PsiFile file = myFixture.configureByFile(fileName);
          JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);
          PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
          PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
          myFixture.checkResultByFile(getTestName(false) + "_after" + ".java");
          PsiTestUtil.checkFileStructure(file);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      });
    });
  }

  public void testModuleImportWithDefaultPackageImport() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), ()->{
      addCode("module-info.java", """
      module my.source.moduleB {
        exports my.source.moduleB;
      }
      """, M2);
        addCode("my/source/moduleB/String.java", """
      package my.source.moduleB;
      public class String {}
      """, M2);
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
      PsiClass psiClass = getPsiClass();
      assertNotNull(psiClass);
      assertEquals("java.lang.String", psiClass.getQualifiedName());
    });
  }

  public void testAmbiguousModuleImportWithPackageImport() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.MODULE_IMPORT_DECLARATIONS.getMinimumLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
    });
  }

    public void testModuleImportWithSingleImport() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), ()->{
      prepareAmbiguousModuleTests();
      myFixture.configureByFile(getTestName(false) + ".java");
      myFixture.checkHighlighting();
      PsiClass psiClass = getPsiClass();
      assertNotNull(psiClass);
      assertEquals("my.source.moduleA.Imported", psiClass.getQualifiedName());
    });
  }

  public void testOptimizeImportWithSimilarNames() {
    JavaCodeStyleSettings.getInstance(getProject()).setDeleteUnusedModuleImports(false);
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

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String fileName = getTestName(false) + ".java";
      try {
        PsiFile file = myFixture.configureByFile(fileName);

        JavaCodeStyleManager.getInstance(getProject()).optimizeImports(file);
        PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        myFixture.checkResultByFile(getTestName(false) + "_after" + ".java");
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
    PsiField field = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), PsiField.class);
    assertNotNull(field);
    return PsiUtil.resolveClassInClassTypeOnly(field.getType());
  }

  private ResolveResult[] getResolveResults() {
    PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    return ((PsiPolyVariantReference)reference).multiResolve(false);
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text, @NotNull ModuleDescriptor descriptor) {
    addFile(name, text, descriptor);
  }

  private void addCode(@NotNull String name, @NotNull @Language("JAVA") String text) {
    myFixture.configureByText(name, text);
  }
}
