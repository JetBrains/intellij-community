// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.nio.file.Path

class BaseNewProjectSettings(private val context: WizardContext) {
  val propertyGraph = PropertyGraph()

  val nameProperty = propertyGraph.graphProperty { suggestName() }
  val pathProperty = propertyGraph.graphProperty { context.projectFileDirectory }
  val gitProperty = propertyGraph.graphProperty { false }

  var name by nameProperty
  var path by pathProperty
  var git by gitProperty

  val projectPath: Path get() = Path.of(path, name)

  private fun suggestName(): String {
    val moduleNames = findAllModules().map { it.name }.toSet()
    return FileUtil.createSequentFileName(File(path), "untitled", "") {
      !it.exists() && it.name !in moduleNames
    }
  }

  private fun findAllModules(): List<Module> {
    val project = context.project ?: return emptyList()
    val moduleManager = ModuleManager.getInstance(project)
    return moduleManager.modules.toList()
  }

  companion object {
    val KEY = Key.create<BaseNewProjectSettings>(BaseNewProjectSettings::class.java.name)
  }
}