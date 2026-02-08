// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.roots.libraries

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@TestApplication
@RunInEdt(writeIntent = true)
class LibraryScopeCacheTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val sdkDir: TempDirectoryExtension = TempDirectoryExtension()


  @Test
  fun `library used in single module`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val module = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val libraryFile = projectModel.baseProjectDir.newVirtualFile("lib/Lib.class")
    val library = projectModel.addProjectLevelLibrary("testLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    val moduleSourceRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
    val moduleBSourceRoot = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)

    val scope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(scope.contains(libraryFile), "Library scope should contain library files")
    assertTrue(scope.contains(moduleSourceRoot), "Library scope should contain module source that uses the library")
    assertFalse(scope.contains(moduleBSourceRoot), "Library scope should NOT contain module that doesn't use the library")
  }

  @Test
  fun `library used in two modules with compile scope`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("sharedLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleA, library, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(moduleB, library, DependencyScope.COMPILE, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)

    val scope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(scope.contains(sourceRootA), "Library scope should contain moduleA sources")
    assertTrue(scope.contains(sourceRootB), "Library scope should contain moduleB sources")
    assertFalse(scope.contains(sourceRootC), "Library scope should NOT contain moduleC which doesn't use the library")
  }

  @Test
  fun `library with test scope`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val module = projectModel.createModule("moduleA")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("testLib")
    val library = projectModel.addProjectLevelLibrary("testScopeLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(module, library, DependencyScope.TEST, false)

    val sourceRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)

    val scope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(scope.contains(sourceRoot), "Library scope should contain module sources even for TEST scope library")
  }

  @Test
  fun `libraries only scope contains only library files`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val module = projectModel.createModule("moduleA")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val libraryFile = projectModel.baseProjectDir.newVirtualFile("lib/Lib.class")
    val library = projectModel.addProjectLevelLibrary("onlyLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
    val sourceFile = projectModel.baseProjectDir.newVirtualFile("moduleA/src/Main.java")

    val librariesOnlyScope = libraryScopeCache.librariesOnlyScope

    assertTrue(librariesOnlyScope.contains(libraryFile), "Libraries only scope should contain library files")
    assertFalse(librariesOnlyScope.contains(sourceFile), "Libraries only scope should NOT contain module source files")
  }

  @Test
  fun `library scope with sources`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val module = projectModel.createModule("moduleA")

    val libraryClassesRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/classes")
    val librarySourcesRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/sources")
    val library = projectModel.addProjectLevelLibrary("libWithSources") {
      it.addRoot(libraryClassesRoot, OrderRootType.CLASSES)
      it.addRoot(librarySourcesRoot, OrderRootType.SOURCES)
    }
    ModuleRootModificationUtil.addDependency(module, library)

    val scope = libraryScopeCache.getLibraryScope(libraryClassesRoot)

    assertTrue(scope.contains(libraryClassesRoot), "Library scope should contain library classes")
  }

  @Test
  fun `multiple modules with different dependency scopes on same library`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("multiScopeLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleA, library, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(moduleB, library, DependencyScope.TEST, false)
    ModuleRootModificationUtil.addDependency(moduleC, library, DependencyScope.PROVIDED, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)

    val scope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(scope.contains(sourceRootA), "Library scope should contain moduleA sources (COMPILE)")
    assertTrue(scope.contains(sourceRootB), "Library scope should contain moduleB sources (TEST)")
    assertTrue(scope.contains(sourceRootC), "Library scope should contain moduleC sources (PROVIDED)")
  }

  @Test
  fun `library use scope includes dependent modules transitively`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, DependencyScope.COMPILE, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val useScope = libraryScopeCache.getLibraryUseScope(libraryRoot)

    assertTrue(useScope.contains(sourceRootC), "Library use scope should contain moduleC (direct dependency)")
    assertTrue(useScope.contains(sourceRootB), "Library use scope should contain moduleB (via exported from moduleC)")
    assertTrue(useScope.contains(sourceRootA), "Library use scope should contain moduleA (via exported chain)")
    assertFalse(useScope.contains(sourceRootD), "Library use scope should NOT contain moduleD (not in dependency chain)")
  }

  @Test
  fun `module level library scope`() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val module = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("moduleLib")
    projectModel.addModuleLevelLibrary(module, "moduleLevelLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    val sourceRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)
    val moduleBSourceRoot = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val scope = libraryScopeCache.getLibraryScope(libraryRoot)
    assertTrue(scope.contains(sourceRoot), "Module-level library scope should contain the module's sources")
    assertFalse(scope.contains(moduleBSourceRoot), "Module-level library scope should NOT contain other module's sources")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library use scope with exported library and exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, true)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, true)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val useScope = libraryScopeCache.getLibraryUseScope(libraryRoot)

    assertTrue(useScope.contains(sourceRootC), "Library use scope should contain moduleC (direct dependency)")
    assertTrue(useScope.contains(sourceRootB), "Library use scope should contain moduleB (depends on moduleC)")
    assertTrue(useScope.contains(sourceRootA), "Library use scope should contain moduleA (depends on moduleB)")
    assertFalse(useScope.contains(sourceRootD), "Library use scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library use scope with exported library and non-exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, true)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, false)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val useScope = libraryScopeCache.getLibraryUseScope(libraryRoot)

    assertTrue(useScope.contains(sourceRootC), "Library use scope should contain moduleC (direct dependency)")
    assertTrue(useScope.contains(sourceRootB), "Library use scope should contain moduleB (depends on moduleC)")
    assertTrue(useScope.contains(sourceRootA), "Library use scope should contain moduleA (depends on moduleB)")
    assertFalse(useScope.contains(sourceRootD), "Library use scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library use scope with non-exported library and exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, false)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, true)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val useScope = libraryScopeCache.getLibraryUseScope(libraryRoot)

    assertTrue(useScope.contains(sourceRootC), "Library use scope should contain moduleC (direct dependency)")
    assertTrue(useScope.contains(sourceRootB), "Library use scope should contain moduleB (depends on moduleC)")
    assertTrue(useScope.contains(sourceRootA), "Library use scope should contain moduleA (depends on moduleB)")
    assertFalse(useScope.contains(sourceRootD), "Library use scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library use scope with non-exported library and non-exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, false)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, false)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val useScope = libraryScopeCache.getLibraryUseScope(libraryRoot)

    assertTrue(useScope.contains(sourceRootC), "Library use scope should contain moduleC (direct dependency)")
    assertTrue(useScope.contains(sourceRootB), "Library use scope should contain moduleB (depends on moduleC)")
    assertFalse(useScope.contains(sourceRootA), "Library use scope should not contain moduleA")
    assertFalse(useScope.contains(sourceRootD), "Library use scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library scope with exported library and exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, true)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, true)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val libraryScope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(libraryScope.contains(sourceRootC), "Library scope should contain moduleC (direct dependency)")
    assertTrue(libraryScope.contains(sourceRootB), "Library scope should contain moduleB (library exported from C)")
    assertTrue(libraryScope.contains(sourceRootA), "Library scope should contain moduleA (module exported from B)")
    assertFalse(libraryScope.contains(sourceRootD), "Library scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library scope with exported library and non-exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, true)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, false)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val libraryScope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(libraryScope.contains(sourceRootC), "Library scope should contain moduleC (direct dependency)")
    assertTrue(libraryScope.contains(sourceRootB), "Library scope should contain moduleB (library exported from C)")
    assertFalse(libraryScope.contains(sourceRootA), "Library scope should NOT contain moduleA (module B→C not exported)")
    assertFalse(libraryScope.contains(sourceRootD), "Library scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library scope with non-exported library and exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, false)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, true)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val libraryScope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(libraryScope.contains(sourceRootC), "Library scope should contain moduleC (direct dependency)")
    assertFalse(libraryScope.contains(sourceRootB), "Library scope should NOT contain moduleB (library not exported)")
    assertFalse(libraryScope.contains(sourceRootA), "Library scope should NOT contain moduleA")
    assertFalse(libraryScope.contains(sourceRootD), "Library scope should NOT contain moduleD (not in dependency chain)")
  }

  @ParameterizedTest
  @EnumSource(DependencyScope::class)
  fun `library scope with non-exported library and non-exported module dependency`(scope: DependencyScope) {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    val moduleA = projectModel.createModule("moduleA")
    val moduleB = projectModel.createModule("moduleB")
    val moduleC = projectModel.createModule("moduleC")
    val moduleD = projectModel.createModule("moduleD")

    val libraryRoot = projectModel.baseProjectDir.newVirtualDirectory("lib")
    val library = projectModel.addProjectLevelLibrary("chainLib") {
      it.addRoot(libraryRoot, OrderRootType.CLASSES)
    }

    ModuleRootModificationUtil.addDependency(moduleC, library, scope, false)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC, scope, false)
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, scope, false)

    val sourceRootA = projectModel.addSourceRoot(moduleA, "src", JavaSourceRootType.SOURCE)
    val sourceRootB = projectModel.addSourceRoot(moduleB, "src", JavaSourceRootType.SOURCE)
    val sourceRootC = projectModel.addSourceRoot(moduleC, "src", JavaSourceRootType.SOURCE)
    val sourceRootD = projectModel.addSourceRoot(moduleD, "src", JavaSourceRootType.SOURCE)

    val libraryScope = libraryScopeCache.getLibraryScope(libraryRoot)

    assertTrue(libraryScope.contains(sourceRootC), "Library scope should contain moduleC (direct dependency)")
    assertFalse(libraryScope.contains(sourceRootB), "Library scope should NOT contain moduleB (library not exported)")
    assertFalse(libraryScope.contains(sourceRootA), "Library scope should NOT contain moduleA")
    assertFalse(libraryScope.contains(sourceRootD), "Library scope should NOT contain moduleD (not in dependency chain)")
  }

  @Test
  fun testSdkUseScopeContainsModuleSourcesWithInheritedSdk() {
    val libraryScopeCache = LibraryScopeCache.getInstance(projectModel.project)
    libraryScopeCache.clear()

    val module = projectModel.createModule("moduleWithSdk")
    val sdkRoot = sdkDir.newVirtualDirectory("sdk")
    val sdkFile = sdkDir.newVirtualFile("sdk/SomeClass.class")

    val sdk = projectModel.addSdk("testSdk") {
      it.addRoot(sdkRoot, OrderRootType.CLASSES)
    }

    runWriteActionAndWait {
      ProjectRootManager.getInstance(projectModel.project).projectSdk = sdk
    }
    ModuleRootModificationUtil.setSdkInherited(module)

    val sourceRoot = projectModel.addSourceRoot(module, "src", JavaSourceRootType.SOURCE)

    val useScope = libraryScopeCache.getLibraryUseScope(sdkFile)

    assertTrue(useScope.contains(sourceRoot),
      "SDK use scope should contain source root of module that inherits the SDK")
  }
}
