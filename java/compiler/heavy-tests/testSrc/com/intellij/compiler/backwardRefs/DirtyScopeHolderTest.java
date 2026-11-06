// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DirtyScopeHolderTest extends JavaCodeInsightFixtureTestCase {
  private final Map<Module, VirtualFile> myFiles = new HashMap<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFiles.put(getModule(), myFixture.addFileToProject("Main.java", "class Main {}").getVirtualFile());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFiles.clear();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testDirtyScopeContainsDirtyModulesAndTheirDependents() {
    var a = addModule("A");
    var exportsA = addModule("ExportsA");
    var usesExportsA = addModule("UsesExportsA");
    var usesA = addModule("UsesA");
    var usesUsesA = addModule("UsesUsesA");
    var leaf = addModule("Leaf");
    addModule("Clean");
    ModuleRootModificationUtil.addDependency(exportsA, a, DependencyScope.COMPILE, true);
    ModuleRootModificationUtil.addDependency(usesExportsA, exportsA);
    ModuleRootModificationUtil.addDependency(usesA, a);
    ModuleRootModificationUtil.addDependency(usesUsesA, usesA);

    var holder = createDirtyScopeHolder(getProject(), getTestRootDisposable());
    markDirty(getProject(), holder, Set.of(a, leaf));

    assertEquals(Set.of(a, leaf), holder.getAllDirtyModules());
    assertEquals(Set.of(a, exportsA, usesExportsA, usesA, leaf), modulesWithFileInDirtyScope(holder));
  }

  public void testDirtyScopeOfOneDirtyModule() {
    var a = addModule("A");
    var usesA = addModule("UsesA");
    addModule("Other");
    ModuleRootModificationUtil.addDependency(usesA, a);

    var holder = createDirtyScopeHolder(getProject(), getTestRootDisposable());
    markDirty(getProject(), holder, Set.of(a));

    assertEquals(Set.of(a, usesA), modulesWithFileInDirtyScope(holder));
  }

  public void testDirtyScopeOfModulesWithCommonDependent() {
    var a = addModule("A");
    var b = addModule("B");
    var usesAAndB = addModule("UsesAAndB");
    addModule("Other");
    ModuleRootModificationUtil.addDependency(usesAAndB, a);
    ModuleRootModificationUtil.addDependency(usesAAndB, b);

    var holder = createDirtyScopeHolder(getProject(), getTestRootDisposable());
    markDirty(getProject(), holder, Set.of(a, b));

    assertEquals(Set.of(a, b, usesAAndB), modulesWithFileInDirtyScope(holder));
  }

  public void testNoDirtyModules() {
    addModule("A");

    var holder = createDirtyScopeHolder(getProject(), getTestRootDisposable());
    markDirty(getProject(), holder, Set.of());

    assertEmpty(holder.getAllDirtyModules());
    assertEmpty(modulesWithFileInDirtyScope(holder));
  }

  private @NotNull Module addModule(@NotNull String name) {
    var file = myFixture.addFileToProject(name + "/" + name + ".java", "class " + name + " {}").getVirtualFile();
    var module = PsiTestUtil.addModule(getProject(), JavaModuleType.getModuleType(), name, file.getParent());
    myFiles.put(module, file);
    return module;
  }

  private @NotNull Set<Module> modulesWithFileInDirtyScope(@NotNull DirtyScopeHolder holder) {
    var result = new HashSet<Module>();
    myFiles.forEach((module, file) -> {
      if (holder.contains(file)) result.add(module);
    });
    return result;
  }

  /**
   * Makes the holder see a finished up-to-date check that compiled every module except {@code dirtyModules}.
   */
  static void markDirty(@NotNull Project project, @NotNull DirtyScopeHolder holder, @NotNull Collection<Module> dirtyModules) {
    var allModules = List.of(ModuleManager.getInstance(project).getModules());
    var compiledModules = new ArrayList<>(allModules);
    compiledModules.removeAll(dirtyModules);
    holder.compilerActivityStarted();
    holder.upToDateCheckFinished(allModules, compiledModules);
  }

  /**
   * Creates a holder for Java files. No compiler event and no modification tracker changes the holder.
   */
  static @NotNull DirtyScopeHolder createDirtyScopeHolder(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new DirtyScopeHolder(project,
                                Set.of(JavaFileType.INSTANCE),
                                ProjectRootManager.getInstance(project).getFileIndex(),
                                parentDisposable,
                                ModificationTracker.NEVER_CHANGED,
                                (_, _) -> { });
  }
}
