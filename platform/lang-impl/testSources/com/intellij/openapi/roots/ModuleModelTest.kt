// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.NotNull
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ModuleModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `add remove module`() {
    val manager = projectModel.moduleManager

    val a = edit { model ->
      assertThat(model.isChanged).isFalse()
      assertThat(model.modules).isEmpty()
      val a = projectModel.createModule("a", model)
      assertThat(model.isChanged).isTrue()
      assertThat(model.modules).containsExactly(a)
      assertThat(model.getActualName(a)).isEqualTo("a")
      assertThat(model.getNewName(a)).isNull()
      assertThat(model.getModuleToBeRenamed("a")).isNull()
      assertThat(model.getModuleGroupPath(a)).isNull()
      assertThat(model.hasModuleGroups()).isFalse()
      a
    }

    assertThat(manager.modules).containsExactly(a)
    assertThat(getSortedModules()).containsExactly(a)
    assertThat(manager.findModuleByName("a")).isEqualTo(a)
    assertThat(manager.getModuleGroupPath(a)).isNull()
    assertThat(manager.hasModuleGroups()).isFalse()


    edit { model ->
      assertThat(model.modules).containsExactly(a)
      model.disposeModule(a)
      assertThat(model.isChanged).isTrue()
      assertThat(model.modules).isEmpty()
      assertThat(model.findModuleByName("a")).isNull()
    }

    assertThat(manager.modules).isEmpty()
    assertThat(getSortedModules()).isEmpty()
    assertThat(manager.findModuleByName("a")).isNull()
    assertThat(a.isDisposed).isTrue()
  }

  @Test
  fun `rename module`() {
    val manager = projectModel.moduleManager

    val module = edit { model ->
      val module = projectModel.createModule("a", model)
      model.renameModule(module, "b")
      assertThat(model.isChanged).isTrue()
      assertThat(model.modules).containsExactly(module)
      assertThat(model.findModuleByName("a")).isEqualTo(module)
      assertThat(model.findModuleByName("b")).isNull()
      assertThat(model.getModuleToBeRenamed("a")).isNull()
      assertThat(model.getNewName(module)).isEqualTo("b")
      assertThat(module.name).isEqualTo("a")
      module
    }

    assertThat(manager.findModuleByName("b")).isEqualTo(module)
    assertThat(module.name).isEqualTo("b")

    edit { model ->
      model.renameModule(module, "c")
      assertThat(model.isChanged).isTrue()
      assertThat(model.modules).containsExactly(module)
      assertThat(model.findModuleByName("b")).isEqualTo(module)
      assertThat(model.findModuleByName("c")).isNull()
      assertThat(model.getModuleToBeRenamed("b")).isNull()
      assertThat(model.getNewName(module)).isEqualTo("c")
      assertThat(module.name).isEqualTo("b")
    }

    assertThat(manager.modules).containsExactly(module)
    assertThat(manager.findModuleByName("b")).isNull()
    assertThat(manager.findModuleByName("c")).isEqualTo(module)
    assertThat(module.name).isEqualTo("c")
  }

  @Test
  fun `remove model before committing`() {
    val a = edit { model ->
      val a = projectModel.createModule("a", model)
      model.disposeModule(a)
      assertThat(model.isChanged).isFalse()
      assertThat(model.modules).isEmpty()
      assertThat(model.findModuleByName("a")).isNull()
      a
    }
    assertThat(projectModel.moduleManager.modules).isEmpty()
    assertThat(a.isDisposed).isTrue()
  }

  @Test
  fun `add module and dispose model`() {
    val a = projectModel.createModule("a")
    val model = createModifiableModuleModel()
    val b = projectModel.createModule("b", model)
    assertThat(model.modules).containsExactlyInAnyOrder(a, b)
    runWriteActionAndWait { model.dispose() }
    assertThat(b.isDisposed).isTrue()
    assertThat(a.isDisposed).isFalse()
    assertThat(projectModel.moduleManager.modules).containsExactly(a)
  }

  @Test
  fun `remove module and dispose model`() {
    val a = projectModel.createModule("a")
    val model = createModifiableModuleModel()
    model.disposeModule(a)
    assertThat(model.modules).isEmpty()
    runWriteActionAndWait { model.dispose() }
    assertThat(a.isDisposed).isFalse()
    assertThat(projectModel.moduleManager.modules).containsExactly(a)
  }

  @Test
  fun `commit root model before committing module model`() {
    val library = projectModel.addProjectLevelLibrary("lib")
    val moduleModel = createModifiableModuleModel()
    val module = projectModel.createModule("a", moduleModel)
    val model = createModifiableModel(module)
    model.addLibraryEntry(library)
    val committed = commitModifiableRootModel(model)
    val libraryEntry = dropModuleSourceEntry(committed, 1).single() as LibraryOrderEntry
    assertThat(libraryEntry.library).isEqualTo(library)
    runWriteActionAndWait { moduleModel.commit() }
    val libraryEntryForCommitted = dropModuleSourceEntry(ModuleRootManager.getInstance(module), 1).single() as LibraryOrderEntry
    assertThat(libraryEntryForCommitted.library).isEqualTo(library)
  }

  @Test
  fun `commit root model and dispose module`() {
    val library = projectModel.addProjectLevelLibrary("lib")
    val moduleModel = createModifiableModuleModel()
    val module = projectModel.createModule("a", moduleModel)
    val model = createModifiableModel(module)
    model.addLibraryEntry(library)
    commitModifiableRootModel(model)
    runWriteActionAndWait { moduleModel.dispose() }
    assertThat(projectModel.moduleManager.modules).isEmpty()
  }

  @Test
  fun `create two modules with dependency between them`() {
    val moduleModel = createModifiableModuleModel()
    val a = projectModel.createModule("a", moduleModel)
    val b = projectModel.createModule("b", moduleModel)
    assertThat(moduleModel.modules).containsExactlyInAnyOrder(a, b)
    val model = createModifiableModel(a)
    model.addModuleOrderEntry(b)
    runWriteActionAndWait { ModifiableModelCommitter.multiCommit(listOf(model), moduleModel) }
    val moduleManager = projectModel.moduleManager
    assertThat(moduleManager.modules).containsExactlyInAnyOrder(a, b)
    assertThat(getSortedModules()).containsExactly(b, a)
    runReadAction {
      assertThat(moduleManager.isModuleDependent(a, b)).isTrue()
      assertThat(moduleManager.isModuleDependent(b, a)).isFalse()
    }
    assertThat(moduleManager.findModuleByName("a")).isEqualTo(a)
    assertThat(ModuleRootManager.getInstance(a).dependencies.single()).isEqualTo(b)
  }

  @Test
  fun `create two modules with circular dependency between them`() {
    val moduleModel = createModifiableModuleModel()
    val a = projectModel.createModule("a", moduleModel)
    val b = projectModel.createModule("b", moduleModel)
    val modelA = createModifiableModel(a)
    modelA.addModuleOrderEntry(b)
    val modelB = createModifiableModel(b)
    modelB.addModuleOrderEntry(a)
    runWriteActionAndWait { ModifiableModelCommitter.multiCommit(listOf(modelA, modelB), moduleModel) }
    val moduleManager = projectModel.moduleManager
    assertThat(moduleManager.modules).containsExactlyInAnyOrder(a, b)
    assertThat(getSortedModules()).containsExactlyInAnyOrder(a, b)
    assertThat(moduleManager.findModuleByName("a")).isEqualTo(a)
    assertThat(moduleManager.findModuleByName("b")).isEqualTo(b)
    runReadAction {
      assertThat(moduleManager.isModuleDependent(a, b)).isTrue()
      assertThat(moduleManager.isModuleDependent(b, a)).isTrue()
    }
    assertThat(ModuleRootManager.getInstance(a).dependencies.single()).isEqualTo(b)
    assertThat(ModuleRootManager.getInstance(b).dependencies.single()).isEqualTo(a)
  }

  private fun createModifiableModuleModel(): @NotNull ModifiableModuleModel {
    //we need to get module manager outside of read action because it may lazily initialize the project requiring write action
    val moduleManager = projectModel.moduleManager
    return runReadAction { moduleManager.modifiableModel }
  }

  private fun getSortedModules() = runReadAction { projectModel.moduleManager.sortedModules }

  private fun <T> edit(action: (ModifiableModuleModel) -> T): T {
    val model = createModifiableModuleModel()
    checkConsistency(model)
    val result = action(model)
    checkConsistency(model)
    runWriteActionAndWait { model.commit() }
    return result
  }

  private fun checkConsistency(model: ModifiableModuleModel) {
    for (module in model.modules) {
      val newName = model.getNewName(module)
      if (newName != null) {
        assertThat(model.getActualName(module)).isEqualTo(newName)
        assertThat(model.getModuleToBeRenamed(newName)).isEqualTo(module)
      }
      else {
        assertThat(model.getActualName(module)).isEqualTo(module.name)
      }
      assertThat(model.findModuleByName(module.name)).isEqualTo(module)
    }
  }
}