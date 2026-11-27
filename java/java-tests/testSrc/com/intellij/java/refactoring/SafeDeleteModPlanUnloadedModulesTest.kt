// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightFixture
import com.intellij.model.SymbolPointer
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanConflictKind
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.usages.impl.UnknownUsagesInUnloadedModules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class SafeDeleteModPlanUnloadedModulesTest {
  private val fixture by heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath())

  /** The usages in a module that is not loaded are unknown, so the deletion of a method that module may use is broken as a whole. */
  @Test
  fun `test an unloaded module that depends on the module of the method is a broken conflict of the whole deletion`(): Unit = timeoutRunBlocking {
    val project = fixture.project
    val a = fixture.createModule("a")
    val b = fixture.createModule("b")
    ModuleRootModificationUtil.addDependency(b, a)
    val sourceRoot = fixture.createTempVirtualDir()
    PsiTestUtil.addSourceRoot(a, sourceRoot)
    val file = edtWriteAction { VfsTestUtil.createFile(sourceRoot, "Foo.java", "public class Foo { public void bar() {} }") }
    val moduleManager = ModuleManager.getInstance(project)
    moduleManager.setUnloadedModules(listOf("b"))
    val method = readAction { (PsiManager.getInstance(project).findFile(file) as PsiJavaFile).classes.single().methods.single() }

    val plan = readAction {
      ModPlanSafeDeleteRefactoring.getInstance().plan(ModPlanRequest(project, listOf(SymbolPointer.of(method)), ModPlanOptionValues.EMPTY))
    }

    val conflict = (plan as ModPlan.AcceptedPlan).conflicts.conflicts.single()
    assertNull(conflict.location)
    assertEquals(ModPlanConflictKind.BROKEN, conflict.kind)
    assertEquals(listOf(UnknownUsagesInUnloadedModules(moduleManager.unloadedModuleDescriptions).presentation.plainText), conflict.messages)
  }
}
