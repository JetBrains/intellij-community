// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderEnumerationHandler;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.util.io.TestFileSystemBuilder.fs;

public class ModuleCompileScopeTest extends BaseCompilerTestCase {
  public void testCompileFile() {
    VirtualFile a = createFile("src/A.java", "class A{}");
    createFile("src/B.java", "class B{}");
    Module module = addModule("a", a.getParent());
    compile(true, a);
    assertOutput(module, fs().file("A.class"));
    make(module);
    assertOutput(module, fs().file("A.class").file("B.class"));
    assertModulesUpToDate();
  }

  public void testForceCompileUpToDateFile() {
    VirtualFile a = createFile("src/A.java", "class A{}");
    Module module = addModule("a", a.getParent());
    make(module);
    assertOutput(module, fs().file("A.class"));
    final VirtualFile output = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module));
    assertNotNull(output);
    final VirtualFile classFile = output.findChild("A.class");
    assertNotNull(classFile);
    deleteFile(classFile);
    make(module);
    assertOutput(module, fs());
    compile(true, a);
    assertOutput(module, fs().file("A.class"));
    assertModulesUpToDate();
  }

  public void testForceCompileUpToDateFileAndDoNotCompileResources() {
    VirtualFile a = createFile("src/A.java", "class A{}");
    createFile("src/res.properties", "aaa=bbb");
    Module module = addModule("a", a.getParent());
    make(module);
    assertOutput(module, fs().file("A.class").file("res.properties"));
    final VirtualFile output = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module));
    assertNotNull(output);
    final VirtualFile classFile = output.findChild("A.class");
    assertNotNull(classFile);
    final VirtualFile resOutputFile = output.findChild("res.properties");
    assertNotNull(resOutputFile);
    final File resourceOutputIoFile = new File(resOutputFile.getPath());
    final long resStampBefore = resourceOutputIoFile.lastModified();
    deleteFile(classFile);
    make(module);
    assertOutput(module, fs().file("res.properties"));
    compile(true, a);
    assertOutput(module, fs().file("A.class").file("res.properties"));
    final long resStampAfter = resourceOutputIoFile.lastModified();
    assertEquals(resStampBefore, resStampAfter);
    assertModulesUpToDate();
  }

  public void testForceCompileResourcesAndDoNotCompileSourceFiles() {
    VirtualFile a = createFile("src/A.java", "class A{}");
    VirtualFile b = createFile("res/res.properties", "aaa=bbb");
    Module module = addModule("a", a.getParent(), null, b.getParent());
    make(module);
    assertOutput(module, fs().file("A.class").file("res.properties"));
    final VirtualFile output = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module));
    assertNotNull(output);
    final VirtualFile classFile = output.findChild("A.class");
    assertNotNull(classFile);
    final VirtualFile resOutputFile = output.findChild("res.properties");
    assertNotNull(resOutputFile);
    final File classOutputIoFile = new File(classFile.getPath());
    final long classStampBefore = classOutputIoFile.lastModified();

    deleteFile(resOutputFile);
    changeFile(a, "class A { }"); // touch java source
    assertOutput(module, fs().file("A.class"));
    compile(true, b);

    assertOutput(module, fs().file("A.class").file("res.properties"));
    final long classStampAfter = classOutputIoFile.lastModified();
    assertEquals("Java source should not be recompiled", classStampBefore, classStampAfter);
  }

  public void testForceCompileUpToDateFileAndDoNotCompileDependentTestClass() {
    VirtualFile a = createFile("src/A.java", "class A{ public static void foo(int param) {} }");
    final String bText = "class B { void bar() {A.foo(10);}}";
    VirtualFile b = createFile("testSrc/B.java", bText);
    Module module = addModule("a", a.getParent(), b.getParent(), null);
    make(module);
    assertOutput(module, fs().file("A.class"), false);
    assertOutput(module, fs().file("B.class"), true);
    final VirtualFile output = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module, false));
    assertNotNull(output);
    final VirtualFile testOutput = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module, true));
    assertNotNull(testOutput);
    final VirtualFile classFile = output.findChild("A.class");
    assertNotNull(classFile);
    final VirtualFile testClassFile = testOutput.findChild("B.class");
    assertNotNull(testClassFile);
    deleteFile(classFile);
    deleteFile(testClassFile);
    make(module);
    assertOutput(module, fs());
    changeFile(b, bText + "  "); // touch b

    compile(true, a);
    assertOutput(module, fs().file("A.class"), false);
    assertOutput(module, fs(), true);  // make sure B is not compiled, even if it is modified
  }

  public void testMakeProductionClassesOnly() {
    final String aText = "class A{ public static void foo(int param) {} }";
    VirtualFile a = createFile("src/A.java", aText);
    final String bText = "class B { void bar() {A.foo(10);}}";
    VirtualFile b = createFile("testSrc/B.java", bText);
    Module module = addModule("a", a.getParent(), b.getParent(), null);
    make(module);
    assertOutput(module, fs().file("A.class"), false);
    assertOutput(module, fs().file("B.class"), true);
    final VirtualFile output = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module, false));
    assertNotNull(output);
    final VirtualFile testOutput = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(module, true));
    assertNotNull(testOutput);
    final VirtualFile classFile = output.findChild("A.class");
    assertNotNull(classFile);
    final VirtualFile testClassFile = testOutput.findChild("B.class");
    assertNotNull(testClassFile);
    deleteFile(classFile);
    deleteFile(testClassFile);
    changeFile(a, aText + "  "); // touch a
    changeFile(b, bText + "  "); // touch b

    make(getCompilerManager().createModulesCompileScope(new Module[] {module}, false, false, false));
    assertOutput(module, fs().file("A.class"), false);
    assertOutput(module, fs(), true); // make sure B is not compiled, even if it is modified
  }

  public void testDoNotCompileDependentTests() {
    Disposable extDisposable = Disposer.newDisposable();

    // emulate behavior for maven-imported projects
    OrderEnumerationHandler.EP_NAME.getPoint().registerExtension(new OrderEnumerationHandler.Factory() {
      private static final OrderEnumerationHandler HANDLER = new OrderEnumerationHandler() {
        @Override
        public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
          return false;
        }
      };

      @Override
      public boolean isApplicable(@NotNull Module module) {
        return true;
      }

      @Override
      public @NotNull OrderEnumerationHandler createHandler(@NotNull Module module) {
        return HANDLER;
      }
    }, extDisposable);

    try {
      String aText = "class A{ public static void foo(int param) {} }";
      VirtualFile a = createFile("m1/src/A.java", aText);
      String bText = "class B { void bar() {A.foo(10);}}";
      VirtualFile b = createFile("m1/testSrc/B.java", bText);
      Module m1 = addModule("m1", a.getParent(), b.getParent(), null);

      String bm2Text = "class BM2{}";
      VirtualFile fileBM2 = createFile("m2/src/BM2.java", bm2Text);
      String testBM2Text = "class TestBM2{}";
      VirtualFile fileTestBM2 = createFile("m2/testSrc/TestBM2.java", testBM2Text);
      Module m2 = addModule("m2", fileBM2.getParent(), fileTestBM2.getParent(), null);

      ModuleRootModificationUtil.addDependency(m1, m2);
      make(m1, m2);

      assertOutput(m1, fs().file("A.class"), false);
      assertOutput(m1, fs().file("B.class"), true);
      assertOutput(m2, fs().file("BM2.class"), false);
      assertOutput(m2, fs().file("TestBM2.class"), true);

      VirtualFile outputM1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m1, false));
      assertNotNull(outputM1);
      final VirtualFile testOutputM1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m1, true));
      assertNotNull(testOutputM1);
      VirtualFile classFileM1 = outputM1.findChild("A.class");
      assertNotNull(classFileM1);
      VirtualFile testClassFileM1 = testOutputM1.findChild("B.class");
      assertNotNull(testClassFileM1);
      deleteFile(classFileM1);
      deleteFile(testClassFileM1);
      changeFile(a, aText + "  "); // touch a
      changeFile(b, bText + "  "); // touch b

      VirtualFile outputM2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m2, false));
      assertNotNull(outputM2);
      VirtualFile testOutputM2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m2, true));
      assertNotNull(testOutputM2);
      VirtualFile classFileM2 = outputM2.findChild("BM2.class");
      assertNotNull(classFileM2);
      VirtualFile testClassFileM2 = testOutputM2.findChild("TestBM2.class");
      assertNotNull(testClassFileM2);
      deleteFile(classFileM2);
      deleteFile(testClassFileM2);
      changeFile(fileBM2, bm2Text + "  "); // touch fileBM2
      changeFile(fileTestBM2, testBM2Text + "  some error"); // touch fileTestBM2, so it won't compile

      make(getCompilerManager().createModulesCompileScope(new Module[] {m1}, true, true, true));
      assertOutput(m1, fs().file("A.class"), false);
      assertOutput(m1, fs().file("B.class"), true);
      assertOutput(m2, fs().file("BM2.class"), false);
      assertOutput(m2, fs(), true); // make sure TestBM2 is not compiled, even if it is modified
    }
    finally {
      Disposer.dispose(extDisposable);
    }
  }

  public void testCompileProductionOnTestsDependency() {
    Disposable extDisposable = Disposer.newDisposable();

    // emulate behavior for maven-imported projects
    OrderEnumerationHandler.EP_NAME.getPoint().registerExtension(new OrderEnumerationHandler.Factory() {
      private static final OrderEnumerationHandler HANDLER = new OrderEnumerationHandler() {
        @Override
        public boolean shouldIncludeTestsFromDependentModulesToTestClasspath() {
          return false;
        }

        @Override
        public boolean shouldProcessDependenciesRecursively() {
          return false;
        }
      };

      @Override
      public boolean isApplicable(@NotNull Module module) {
        return true;
      }

      @Override
      public @NotNull OrderEnumerationHandler createHandler(@NotNull Module module) {
        return HANDLER;
      }
    }, extDisposable);

    try {
      //String aText = "class A{ String msg = TestBM2.message; public static void foo(int param) {} }";
      String aText = "class A{ public static void foo(int param) {} }";
      VirtualFile a = createFile("m1/src/A.java", aText);
      String bText = "class B { void bar() {A.foo(10);}}";
      VirtualFile b = createFile("m1/testSrc/B.java", bText);
      Module m1 = addModule("m1", a.getParent(), b.getParent(), null);

      String testBM2Text = "class TestBM2{public static final String message =\"hello\";}";
      VirtualFile fileTestBM2 = createFile("m2/testSrc/TestBM2.java", testBM2Text);
      Module m2 = addModule("m2", null, fileTestBM2.getParent(), null);

      ModuleRootModificationUtil.addDependency(m1, m2, DependencyScope.COMPILE, false /* exported */, true /* productionOnTests */);
      make(m1, m2);

      assertOutput(m1, fs().file("A.class"), false);
      assertOutput(m1, fs().file("B.class"), true);
      assertOutput(m2, fs().file("TestBM2.class"), true);

      VirtualFile outputM1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m1, false));
      assertNotNull(outputM1);
      final VirtualFile testOutputM1 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m1, true));
      assertNotNull(testOutputM1);
      VirtualFile classFileM1 = outputM1.findChild("A.class");
      assertNotNull(classFileM1);
      VirtualFile testClassFileM1 = testOutputM1.findChild("B.class");
      assertNotNull(testClassFileM1);
      deleteFile(classFileM1);
      deleteFile(testClassFileM1);
      changeFile(a, aText + "  "); // touch a
      changeFile(b, bText + "  some error"); // touch b so it won't compile

      VirtualFile testOutputM2 = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(getOutputDir(m2, true));
      assertNotNull(testOutputM2);
      VirtualFile testClassFileM2 = testOutputM2.findChild("TestBM2.class");
      assertNotNull(testClassFileM2);
      deleteFile(testClassFileM2);
      changeFile(fileTestBM2, testBM2Text + "  "); // touch fileTestBM2

      // The module scope should not contain tests from m1, because includeTests = false.
      // Despite on that, it should contain tests from m2, because productionOnTests = true for the module dependency m1 --> m2
      make(getCompilerManager().createModulesCompileScope(new Module[] {m1}, true /*includeDeps*/, true, false /* include tests */));
      assertOutput(m1, fs().file("A.class"), false);
      assertOutput(m1, fs(), true);
      assertOutput(m2, fs().file("TestBM2.class"), true); // make sure TestBM2 from dependent module is compiled, because it has been included in the compile scope
    }
    finally {
      Disposer.dispose(extDisposable);
    }
  }

  public void testMakeTwoModules() {
    VirtualFile file1 = createFile("m1/src/A.java", "class A{}");
    Module m1 = addModule("m1", file1.getParent());
    VirtualFile file2 = createFile("m2/src/B.java", "class B{}");
    Module m2 = addModule("m2", file2.getParent());
    make(m1);
    assertOutput(m1, fs().file("A.class"));
    assertNoOutput(m2);
    make(m2);
    assertOutput(m2, fs().file("B.class"));
    assertModulesUpToDate();
  }

  public void testMakeDependentModules() {
    VirtualFile file1 = createFile("main/src/A.java", "class A{}");
    Module main = addModule("main", file1.getParent());
    VirtualFile file2 = createFile("dep/src/B.java", "class B{}");
    Module dep = addModule("dep", file2.getParent());
    ModuleRootModificationUtil.addDependency(main, dep);
    makeWithDependencies(false, main);
    assertOutput(main, fs().file("A.class"));
    assertOutput(dep, fs().file("B.class"));
  }

  public void testDoNotIncludeRuntimeDependenciesToCompileScope() {
    VirtualFile file1 = createFile("main/src/A.java", "class A{}");
    Module main = addModule("main", file1.getParent());
    VirtualFile file2 = createFile("dep/src/B.java", "class B{}");
    Module dep = addModule("dep", file2.getParent());
    ModuleRootModificationUtil.addDependency(main, dep, DependencyScope.RUNTIME, false);
    makeWithDependencies(false, main);
    assertOutput(main, fs().file("A.class"));
    assertNoOutput(dep);
    make(dep);
    assertOutput(dep, fs().file("B.class"));
    assertModulesUpToDate();
  }

  public void testIncludeRuntimeDependenciesToCompileScope() {
    VirtualFile file1 = createFile("main/src/A.java", "class A{}");
    Module main = addModule("main", file1.getParent());
    VirtualFile file2 = createFile("dep/src/B.java", "class B{}");
    Module dep = addModule("dep", file2.getParent());
    ModuleRootModificationUtil.addDependency(main, dep, DependencyScope.RUNTIME, false);
    makeWithDependencies(true, main);
    assertOutput(main, fs().file("A.class"));
    assertOutput(dep, fs().file("B.class"));
    assertModulesUpToDate();
  }

  public void testExcludedFile() {
    VirtualFile a = createFile("src/a/A.java", "package a; class A{}");
    createFile("src/b/B.java", "package b; class B{}");
    Module m = addModule("m", a.getParent().getParent());
    PsiTestUtil.addExcludedRoot(m, a.getParent());
    make(m);
    assertOutput(m, fs().dir("b").file("B.class"));

    changeFile(a);
    make(m);
    assertOutput(m, fs().dir("b").file("B.class"));

    rebuild();
    assertOutput(m, fs().dir("b").file("B.class"));
  }

  public void testFileUnderIgnoredFolder() {
    VirtualFile src = createFile("src/A.java", "class A{}").getParent();
    Module m = addModule("m", src);
    make(m);
    assertOutput(m, fs().file("A.class"));

    VirtualFile b = createFile("src/CVS/B.java", "package CVS; class B{}");
    make(m);
    assertOutput(m, fs().file("A.class"));

    changeFile(b);
    make(m);
    assertOutput(m, fs().file("A.class"));

    rebuild();
    assertOutput(m, fs().file("A.class"));
  }
}
