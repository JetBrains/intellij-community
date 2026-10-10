// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.testFramework.JavaModuleTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules
import java.util.concurrent.Callable

class SafeDeleteModPlanUnloadedModulesTest : JavaModuleTestCase() {
  /** The usages in a module that is not loaded are unknown, so the deletion of a method that module may use is broken as a whole. */
  fun `test an unloaded module that depends on the module of the method is a broken conflict of the whole deletion`() {
    val a = createModule("a")
    val b = createModule("b")
    ModuleRootModificationUtil.addDependency(b, a)
    val sourceRoot = getVirtualFile(createTempDirectory())
    PsiTestUtil.addSourceRoot(a, sourceRoot)
    val file = runWriteAction { VfsTestUtil.createFile(sourceRoot, "Foo.java", "public class Foo { public void bar() {} }") }
    val moduleManager = ModuleManager.getInstance(project)
    runWithModalProgressBlocking(project, "") {
      moduleManager.setUnloadedModules(listOf("b"))
    }
    val method = runReadActionBlocking { (PsiManager.getInstance(project).findFile(file) as PsiJavaFile).classes.single().methods.single() }

    val plan = PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(Callable {
      runReadActionBlocking {
        ModPlanSafeDeleteRefactoring.getInstance().plan(ModPlanRequest(project, listOf(SymbolPointer.of(method)), ModPlanOptionValues.EMPTY))
      }
    }))

    val conflict = (plan as ModPlan.AcceptedPlan).conflicts.conflicts.single()
    assertNull(conflict.location)
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    assertEquals(listOf(UnknownUsagesInUnloadedModules(moduleManager.unloadedModuleDescriptions).presentation.plainText), conflict.messages)
  }
}
