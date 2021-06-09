// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.plugins.DependencyCollector
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties

class JavaDependencyCollector : DependencyCollector {
  override fun collectDependencies(project: Project): List<String> {
    val result = mutableSetOf<String>()
    runReadAction {
      val projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      for (library in projectLibraryTable.libraries) {
        val properties = (library as? LibraryEx)?.properties as? RepositoryLibraryProperties ?: continue
        result.add(properties.groupId + ":" + properties.artifactId)
      }
      for (module in ModuleManager.getInstance(project).modules) {
        for (orderEntry in module.rootManager.orderEntries) {
          if (orderEntry is LibraryOrderEntry && orderEntry.isModuleLevel) {
            val library = orderEntry.library
            val properties = (library as? LibraryEx)?.properties as? RepositoryLibraryProperties ?: continue
            result.add(properties.groupId + ":" + properties.artifactId)
          }
        }
      }
    }
    return result.toList()
  }
}