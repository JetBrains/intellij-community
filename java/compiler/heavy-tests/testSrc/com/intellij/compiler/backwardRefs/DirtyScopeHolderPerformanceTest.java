// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Measures the dirty scope of the compiler reference index in a project with many modules.
 * Each module depends on the main module and has {@link #FILES_PER_MODULE} files that use the class {@code Base} of the main module.
 * All modules except each {@link #CLEAN_MODULE_STEP}th module are dirty, as after a branch switch.
 */
@PerformanceUnitTest
public class DirtyScopeHolderPerformanceTest extends JavaCodeInsightFixtureTestCase {
  private static final int MODULES = 2000;
  private static final int FILES_PER_MODULE = 5;
  private static final int CLEAN_MODULE_STEP = 10;
  /** Each file refers to {@code Base} in the import and in the field type. */
  private static final int REFERENCES = 2 * MODULES * FILES_PER_MODULE;

  private DirtyScopeHolder myHolder;
  private final List<VirtualFile> myFirstFileOfEachModule = new ArrayList<>();
  private final List<VirtualFile> myCleanFiles = new ArrayList<>();
  private int myDirtyModules;
  private PsiClass myBase;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myBase = myFixture.addClass("package core; public class Base {}");
    var dirtyModules = createModules();
    myDirtyModules = dirtyModules.size();
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    myHolder = DirtyScopeHolderTest.createDirtyScopeHolder(getProject(), getTestRootDisposable());
    DirtyScopeHolderTest.markDirty(getProject(), myHolder, dirtyModules);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myHolder = null;
      myBase = null;
      myFirstFileOfEachModule.clear();
      myCleanFiles.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Checks one file of each module against the dirty scope.
   */
  public void testContainsWithManyDirtyModules() {
    Benchmark.newBenchmark(getTestName(false), () -> {
      int count = 0;
      for (VirtualFile file : myFirstFileOfEachModule) {
        if (myHolder.contains(file)) count++;
      }
      assertEquals(myDirtyModules, count);
    }).start();
  }

  /**
   * Finds the usages of {@code Base} in the scope that the compiler reference index gives the search,
   * from {@link CompilerReferenceServiceBase#mayContainReferencesScope}.
   * The index knows the usages in the clean modules, and the dirty modules may contain more.
   */
  public void testFindUsagesWithManyDirtyModules() {
    var scope = CompilerReferenceServiceBase.mayContainReferencesScope(getProject(), myCleanFiles, myHolder.getDirtyScope(),
                                                                       Set.of(JavaFileType.INSTANCE));
    var searchScope = GlobalSearchScope.projectScope(getProject()).intersectWith(scope);
    Benchmark.newBenchmark(getTestName(false), () -> {
      assertSize(REFERENCES, ReferencesSearch.search(myBase, searchScope).findAll());
    }).start();
  }

  /**
   * Finds the usages of {@code Base} without the compiler reference index, for a comparison with {@link #testFindUsagesWithManyDirtyModules}.
   */
  public void testFindUsagesWithoutCompilerReferenceIndex() {
    var searchScope = GlobalSearchScope.projectScope(getProject());
    Benchmark.newBenchmark(getTestName(false), () -> {
      assertSize(REFERENCES, ReferencesSearch.search(myBase, searchScope).findAll());
    }).start();
  }

  /**
   * Writes the sources of all modules to the disk and adds the modules in one commit.
   *
   * @return the dirty modules
   */
  private @NotNull List<Module> createModules() throws IOException {
    var root = Path.of(myFixture.getTempDirPath(), "modules");
    for (int i = 0; i < MODULES; i++) {
      var sourceDir = Files.createDirectories(root.resolve("m" + i).resolve("src"));
      for (int j = 0; j < FILES_PER_MODULE; j++) {
        var name = "C" + i + "_" + j;
        Files.writeString(sourceDir.resolve(name + ".java"), "package p" + i + ";\nimport core.Base;\nclass " + name + " { Base base; }\n");
      }
    }
    var rootFile = Objects.requireNonNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root));
    VfsUtil.markDirtyAndRefresh(false, true, true, rootFile);

    var dirtyModules = new ArrayList<Module>();
    WriteAction.runAndWait(() -> {
      var moduleModel = ModuleManager.getInstance(getProject()).getModifiableModel();
      var rootModels = new ArrayList<ModifiableRootModel>();
      for (int i = 0; i < MODULES; i++) {
        var contentRoot = Objects.requireNonNull(rootFile.findChild("m" + i));
        var sourceRoot = Objects.requireNonNull(contentRoot.findChild("src"));
        var module = moduleModel.newModule(contentRoot.toNioPath().resolve("m" + i + ".iml"), JavaModuleType.getModuleType().getId());
        var rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        rootModel.addContentEntry(contentRoot).addSourceFolder(sourceRoot, false);
        rootModel.inheritSdk();
        rootModel.addModuleOrderEntry(getModule());
        rootModels.add(rootModel);

        var children = sourceRoot.getChildren();
        myFirstFileOfEachModule.add(children[0]);
        if (i % CLEAN_MODULE_STEP == 0) {
          myCleanFiles.addAll(List.of(children));
        }
        else {
          dirtyModules.add(module);
        }
      }
      ModifiableModelCommitter.multiCommit(rootModels, moduleModel);
    });
    return dirtyModules;
  }
}
