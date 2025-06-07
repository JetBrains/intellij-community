// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.ui.UIUtil
import java.io.File

class OrderEnumeratorHandlerRegistrationTest : HeavyPlatformTestCase() {
  fun `test unregister order enumeration handler`() {
    val (moduleA, moduleB, moduleC) = runWriteAction {
      listOf("a", "b", "c").map {
        ModuleManager.getInstance(myProject).newModule(File(createTempDirectory(), "$it.iml").systemIndependentPath, EmptyModuleType.EMPTY_MODULE)
      }
    }
    val dummyRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
    ModuleRootModificationUtil.addDependency(moduleA, moduleB)
    ModuleRootModificationUtil.addDependency(moduleB, moduleC)
    val srcRoot = runWriteAction { dummyRoot.createChildDirectory(this, "project-model").createChildDirectory(this, "src") }
    PsiTestUtil.addSourceRoot(moduleC, srcRoot)
    runWithRegisteredExtension(MockOrderEnumerationHandlerFactory(processDependenciesRecursively = false)) {
      val enumerator = ModuleRootManager.getInstance(moduleA).orderEntries().recursively().roots(OrderRootType.SOURCES).usingCache()
      assertEmpty(enumerator.roots)
    }
    val enumerator = ModuleRootManager.getInstance(moduleA).orderEntries().recursively().roots(OrderRootType.SOURCES).usingCache()
    assertEquals(srcRoot, assertOneElement(enumerator.roots))
  }

  fun `test include test roots from dependent modules`() {
    val (moduleA, moduleB) = runWriteAction {
      listOf("a", "b").map {
        ModuleManager.getInstance(myProject).newModule(File(createTempDirectory(), "$it.iml").systemIndependentPath, EmptyModuleType.EMPTY_MODULE)
      }
    }
    val dummyRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
    ModuleRootModificationUtil.addDependency(moduleA, moduleB, DependencyScope.TEST, false)
    val moduleBTestSources = runWriteAction { dummyRoot.createChildDirectory(this, "project-model").createChildDirectory(this, "bTests") }
    PsiTestUtil.addSourceRoot(moduleB, moduleBTestSources, true)

    // recursive
    runWithRegisteredExtension(MockOrderEnumerationHandlerFactory(includeTestsFromDependentModulesToTestClasspath = false)) {
      val enumerator = ModuleRootManager.getInstance(moduleA).orderEntries().recursively().roots(OrderRootType.SOURCES).usingCache()
      assertEmpty(enumerator.roots)
    }

    // non-recursive
    runWithRegisteredExtension(MockOrderEnumerationHandlerFactory(includeTestsFromDependentModulesToTestClasspath = false)) {
      val enumerator = ModuleRootManager.getInstance(moduleA).orderEntries().roots(OrderRootType.SOURCES).usingCache()
      assertEmpty(enumerator.roots)
    }

    val enumerator = ModuleRootManager.getInstance(moduleA).orderEntries().recursively().roots(OrderRootType.SOURCES).usingCache()
    assertEquals(moduleBTestSources, assertOneElement(enumerator.roots))
  }

  private fun runWithRegisteredExtension(factory: OrderEnumerationHandler.Factory, action: () -> Unit) {
    val orderEnumerationDisposable = Disposer.newDisposable()
    registerOrderEnumerationHandler(factory, orderEnumerationDisposable)
    try {
      action()
    }
    finally {
      Disposer.dispose(orderEnumerationDisposable)
    }
  }

  private fun registerOrderEnumerationHandler(factory: OrderEnumerationHandler.Factory, disposable: Disposable) {
    val orderEnumerationDisposable = Disposer.newDisposable()
    Disposer.register(disposable, Disposable {
      runWriteAction {
        Disposer.dispose(orderEnumerationDisposable)
      }
      UIUtil.dispatchAllInvocationEvents()
    })
    OrderEnumerationHandler.EP_NAME.point.registerExtension(factory, orderEnumerationDisposable)
    UIUtil.dispatchAllInvocationEvents()
  }
}

private class MockOrderEnumerationHandlerFactory(
  val processDependenciesRecursively: Boolean = true,
  val includeTestsFromDependentModulesToTestClasspath: Boolean = true,
) : OrderEnumerationHandler.Factory() {
  override fun isApplicable(module: Module): Boolean = true
  override fun createHandler(module: Module): OrderEnumerationHandler = Handler()

  private inner class Handler : OrderEnumerationHandler() {
    override fun shouldProcessDependenciesRecursively(): Boolean =
      processDependenciesRecursively

    override fun shouldIncludeTestsFromDependentModulesToTestClasspath(): Boolean =
      includeTestsFromDependentModulesToTestClasspath
  }
}
