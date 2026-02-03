// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.ProjectModelRule
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ModuleDependencyInRootModelTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  lateinit var mainModule: Module

  @Before
  fun setUp() {
    mainModule = projectModel.createModule("main")
  }

  @Test
  fun `add edit remove module dependency`() {
    val depModule = projectModel.createModule("dep")
    run {
      val model = createModifiableModel(mainModule)
      val entry = model.addModuleOrderEntry(depModule)
      assertThat(dropModuleSourceEntry(model, 1).single() as ModuleOrderEntry).isEqualTo(entry)
      assertThat(entry.scope).isEqualTo(DependencyScope.COMPILE)
      assertThat(entry.isExported).isFalse()
      assertThat(entry.isSynthetic).isFalse()
      assertThat(entry.isValid).isTrue()
      assertThat(entry.moduleName).isEqualTo("dep")
      assertThat(entry.presentableName).isEqualTo("dep")
      assertThat(entry.module).isEqualTo(depModule)
      assertThat(model.findModuleOrderEntry(depModule)).isEqualTo(entry)

      val committed = commitModifiableRootModel(model)
      val committedEntry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(committedEntry.scope).isEqualTo(DependencyScope.COMPILE)
      assertThat(committedEntry.isExported).isFalse()
      assertThat(committedEntry.isSynthetic).isFalse()
      assertThat(committedEntry.isValid).isTrue()
      assertThat(committedEntry.moduleName).isEqualTo("dep")
      assertThat(committedEntry.presentableName).isEqualTo("dep")
      assertThat(committedEntry.module).isEqualTo(depModule)
    }

    run {
      val model = createModifiableModel(mainModule)
      val entry = dropModuleSourceEntry(model, 1).single() as ModuleOrderEntry
      entry.scope = DependencyScope.RUNTIME
      entry.isExported = true
      assertThat(model.findModuleOrderEntry(depModule)).isEqualTo(entry)
      val committed = commitModifiableRootModel(model)
      val committedEntry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(committedEntry.module).isEqualTo(depModule)
      assertThat(committedEntry.scope).isEqualTo(DependencyScope.RUNTIME)
      assertThat(committedEntry.isExported).isTrue()
    }

    run {
      val model = createModifiableModel(mainModule)
      val entry = model.findModuleOrderEntry(depModule)!!
      model.removeOrderEntry(entry)
      assertThat(model.orderEntries).hasSize(1)
      assertThat(model.findModuleOrderEntry(depModule)).isNull()
      val committed = commitModifiableRootModel(model)
      assertThat(committed.orderEntries).hasSize(1)
    }
  }

  @Test
  fun `add same module twice`() {
    val depModule = projectModel.createModule("dep")
    run {
      val model = createModifiableModel(mainModule)
      val entry1 = model.addModuleOrderEntry(depModule)
      val entry2 = model.addModuleOrderEntry(depModule)
      assertThat(entry1.module).isEqualTo(depModule)
      assertThat(entry2.module).isEqualTo(depModule)
      assertThat(model.findModuleOrderEntry(depModule)).isEqualTo(entry1)
      val committed = commitModifiableRootModel(model)
      val (committedEntry1, committedEntry2) = dropModuleSourceEntry(committed, 2)
      assertThat((committedEntry1 as ModuleOrderEntry).module).isEqualTo(depModule)
      assertThat((committedEntry2 as ModuleOrderEntry).module).isEqualTo(depModule)
    }

    run {
      val model = createModifiableModel(mainModule)
      (model.orderEntries[2] as ModuleOrderEntry).scope = DependencyScope.RUNTIME
      model.removeOrderEntry(model.orderEntries[1])
      val committed = commitModifiableRootModel(model)
      val committedEntry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(committedEntry.scope).isEqualTo(DependencyScope.RUNTIME)
      assertThat(committedEntry.module).isEqualTo(depModule)
    }
  }

  @Test
  fun `add multiple dependencies at once`() {
    val dep1 = projectModel.createModule("dep1")
    val dep2 = projectModel.createModule("dep2")
    val model = createModifiableModel(mainModule)
    model.addModuleEntries(listOf(dep1, dep2), DependencyScope.RUNTIME, true)
    fun checkEntry(entry: ModuleOrderEntry) {
      assertThat(entry.isExported).isTrue
      assertThat(entry.scope).isEqualTo(DependencyScope.RUNTIME)
    }

    val (entry1, entry2) = dropModuleSourceEntry(model, 2)
    assertThat((entry1 as ModuleOrderEntry).module).isEqualTo(dep1)
    checkEntry(entry1)
    assertThat((entry2 as ModuleOrderEntry).module).isEqualTo(dep2)
    checkEntry(entry2)
    val (committedEntry1, committedEntry2) = dropModuleSourceEntry(commitModifiableRootModel(model), 2)
    assertThat((committedEntry1 as ModuleOrderEntry).module).isEqualTo(dep1)
    checkEntry(committedEntry1)
    assertThat((committedEntry2 as ModuleOrderEntry).module).isEqualTo(dep2)
    checkEntry(committedEntry2)
  }

  @Test
  fun `remove module dependency if there are several equal entries`() {
    val dep1Module = projectModel.createModule("dep1")
    val dep2Module = projectModel.createModule("dep2")
    run {
      val model = createModifiableModel(mainModule)
      model.addModuleOrderEntry(dep1Module)
      model.addModuleOrderEntry(dep2Module)
      model.addModuleOrderEntry(dep1Module)
      model.addModuleOrderEntry(dep2Module)
      model.addModuleOrderEntry(dep1Module)
      commitModifiableRootModel(model)
    }

    run {
      val model = createModifiableModel(mainModule)
      val orderEntries = model.orderEntries
      assertThat((orderEntries[1] as ModuleOrderEntry).module).isEqualTo(dep1Module)
      assertThat((orderEntries[5] as ModuleOrderEntry).module).isEqualTo(dep1Module)
      model.removeOrderEntry(orderEntries[1])
      model.removeOrderEntry(orderEntries[5])
      val committed = commitModifiableRootModel(model)
      val (entry1, entry2, entry3) = dropModuleSourceEntry(committed, 3)
      assertThat((entry1 as ModuleOrderEntry).module).isEqualTo(dep2Module)
      assertThat((entry2 as ModuleOrderEntry).module).isEqualTo(dep1Module)
      assertThat((entry3 as ModuleOrderEntry).module).isEqualTo(dep2Module)
    }
  }

  @Test
  fun `remove referenced module`() {
    val depModule = projectModel.createModule("dep")
    run {
      val model = createModifiableModel(mainModule)
      model.addModuleOrderEntry(depModule)
      commitModifiableRootModel(model)
    }
    projectModel.removeModule(depModule)

    run {
      val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
      assertThat(entry.module).isNull()
      assertThat(entry.moduleName).isEqualTo("dep")
    }

    val newModule = projectModel.createModule("dep")
    run {
      val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
      assertThat(entry.module).isEqualTo(newModule)
    }
  }

  @Test
  fun `add invalid module`() {
    run {
      val model = createModifiableModel(mainModule)
      val uncommittedEntry = model.addInvalidModuleEntry("foo")
      assertThat(uncommittedEntry.isValid).isFalse()
      assertThat(uncommittedEntry.isSynthetic).isFalse()
      assertThat(uncommittedEntry.presentableName).isEqualTo("foo")
      val committed = commitModifiableRootModel(model)
      val entry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(entry.module).isNull()
      assertThat(entry.isValid).isFalse()
      assertThat(entry.isSynthetic).isFalse()
      assertThat(entry.moduleName).isEqualTo("foo")
      assertThat(entry.presentableName).isEqualTo("foo")
    }

    val fooModule = projectModel.createModule("foo")
    run {
      val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
      assertThat(entry.module).isEqualTo(fooModule)
    }
  }

  @Test
  fun `change order`() {
    val a = projectModel.createModule("a")
    val b = projectModel.createModule("b")
    run {
      val model = createModifiableModel(mainModule)
      model.addModuleOrderEntry(a)
      model.addModuleOrderEntry(b)
      val oldOrder = model.orderEntries
      assertThat(oldOrder).hasSize(3)
      assertThat((oldOrder[1] as ModuleOrderEntry).moduleName).isEqualTo("a")
      assertThat((oldOrder[2] as ModuleOrderEntry).moduleName).isEqualTo("b")
      val newOrder = arrayOf(oldOrder[0], oldOrder[2], oldOrder[1])
      model.rearrangeOrderEntries(newOrder)
      assertThat((model.orderEntries[1] as ModuleOrderEntry).moduleName).isEqualTo("b")
      assertThat((model.orderEntries[2] as ModuleOrderEntry).moduleName).isEqualTo("a")
      val committed = commitModifiableRootModel(model)
      assertThat((committed.orderEntries[1] as ModuleOrderEntry).moduleName).isEqualTo("b")
      assertThat((committed.orderEntries[2] as ModuleOrderEntry).moduleName).isEqualTo("a")
    }

    run {
      val model = createModifiableModel(mainModule)
      val oldOrder = model.orderEntries
      assertThat((oldOrder[1] as ModuleOrderEntry).moduleName).isEqualTo("b")
      assertThat((oldOrder[2] as ModuleOrderEntry).moduleName).isEqualTo("a")
      val newOrder = arrayOf(oldOrder[0], oldOrder[2], oldOrder[1])
      model.rearrangeOrderEntries(newOrder)
      assertThat((model.orderEntries[1] as ModuleOrderEntry).moduleName).isEqualTo("a")
      assertThat((model.orderEntries[2] as ModuleOrderEntry).moduleName).isEqualTo("b")
      model.removeOrderEntry(model.orderEntries[1])
      val committed = commitModifiableRootModel(model)
      val entry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(entry.module).isEqualTo(b)
    }
  }

  @Test
  fun `rename module`() {
    val a = projectModel.createModule("a")
    ModuleRootModificationUtil.addDependency(mainModule, a)
    projectModel.renameModule(a, "b")
    val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
    assertThat(entry.module).isEqualTo(a)
    assertThat(entry.moduleName).isEqualTo("b")
  }

  @Test
  fun `rename module and commit after committing root model`() {
    val a = projectModel.createModule("a")
    val model = createModifiableModel(mainModule)
    model.addModuleOrderEntry(a)
    val moduleModel = runReadAction { projectModel.moduleManager.getModifiableModel() }
    moduleModel.renameModule(a, "b")
    val entry = dropModuleSourceEntry(model, 1).single() as ModuleOrderEntry
    assertThat(entry.module).isEqualTo(a)
    assertThat(entry.moduleName).isEqualTo("a")
    val committed = commitModifiableRootModel(model)
    val committedEntry1 = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
    assertThat(committedEntry1.module).isEqualTo(a)
    assertThat(committedEntry1.moduleName).isEqualTo("a")
    runWriteActionAndWait { moduleModel.commit() }
    val committedEntry2 = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
    assertThat(committedEntry2.module).isEqualTo(a)
    assertThat(committedEntry2.moduleName).isEqualTo("b")
  }

  @Test
  fun `add invalid module and rename module to that name`() {
    val module = projectModel.createModule("foo")
    val model = createModifiableModel(mainModule)
    model.addInvalidModuleEntry("bar")
    commitModifiableRootModel(model)

    projectModel.renameModule(module, "bar")

    val moduleEntry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
    assertThat(moduleEntry.module).isEqualTo(module)
  }

  @Test
  fun `dispose model without committing`() {
    val a = projectModel.createModule("a")
    val model = createModifiableModel(mainModule)
    val entry = model.addModuleOrderEntry(a)
    assertThat(entry.module).isEqualTo(a)
    model.dispose()
    dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 0)
  }

  @Test
  fun `add not yet committed module`() {
    val moduleModel = runReadAction { projectModel.moduleManager.getModifiableModel() }
    val a = projectModel.createModule("a", moduleModel)
    run {
      val model = createModifiableModel(mainModule)
      val entry = model.addModuleOrderEntry(a)
      assertThat(entry.moduleName).isEqualTo("a")
      val committed = commitModifiableRootModel(model)
      val moduleEntry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(moduleEntry.moduleName).isEqualTo("a")
    }
    runWriteActionAndWait { moduleModel.commit() }
    run {
      val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
      assertThat(entry.module).isEqualTo(a)
    }
  }

  @Test
  fun `add not yet committed module and do not commit it`() {
    val moduleModel = runReadAction { projectModel.moduleManager.getModifiableModel() }
    val a = projectModel.createModule("a", moduleModel)
    run {
      val model = createModifiableModel(mainModule)
      val entry = model.addModuleOrderEntry(a)
      assertThat(entry.moduleName).isEqualTo("a")
      val committed = commitModifiableRootModel(model)
      val moduleEntry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(moduleEntry.moduleName).isEqualTo("a")
    }
    runWriteActionAndWait { moduleModel.dispose() }
    run {
      val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
      assertThat(entry.module).isNull()
      assertThat(entry.moduleName).isEqualTo("a")
    }
  }

  @Test
  fun `add not yet committed module with configuration accessor`() {
    val moduleModel = runReadAction { projectModel.moduleManager.getModifiableModel() }
    val a = projectModel.createModule("a", moduleModel)
    run {
      val model = createModifiableModel(mainModule, object : RootConfigurationAccessor() {
        override fun getModule(module: Module?, moduleName: String?): Module? {
          return if (moduleName == "a") a else module
        }
      })
      val entry = model.addModuleOrderEntry(a)
      assertThat(entry.module).isEqualTo(a)
      val committed = commitModifiableRootModel(model)
      val moduleEntry = dropModuleSourceEntry(committed, 1).single() as ModuleOrderEntry
      assertThat(moduleEntry.moduleName).isEqualTo("a")
    }
    runWriteActionAndWait { moduleModel.commit() }
    run {
      val moduleEntry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
      assertThat(moduleEntry.module).isEqualTo(a)
    }
  }

  @Test
  fun `edit order entry located after removed entry`() {
    val foo = projectModel.createModule("foo")
    ModuleRootModificationUtil.addDependency(mainModule, foo)
    val bar = projectModel.createModule("bar")
    ModuleRootModificationUtil.addDependency(mainModule, bar)
    val model = createModifiableModel(mainModule)
    val fooEntry = model.findModuleOrderEntry(foo)!!
    val barEntry = model.findModuleOrderEntry(bar)!!
    model.removeOrderEntry(fooEntry)
    barEntry.scope = DependencyScope.TEST
    commitModifiableRootModel(model)
    val entry = dropModuleSourceEntry(ModuleRootManager.getInstance(mainModule), 1).single() as ModuleOrderEntry
    assertThat(entry.module).isEqualTo(bar)
    assertThat(entry.scope).isEqualTo(DependencyScope.TEST)
  }
}