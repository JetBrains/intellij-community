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

internal class JavaDependencyCollector : DependencyCollector {

  override fun collectDependencies(project: Project): Set<String> {
    return runReadAction {
      val projectLibraries = LibraryTablesRegistrar.getInstance()
        .getLibraryTable(project)
        .libraries.asSequence()

      val moduleLibraries = ModuleManager.getInstance(project)
        .modules.asSequence()
        .flatMap { it.rootManager.orderEntries.asSequence() }
        .filterIsInstance<LibraryOrderEntry>()
        .filter { it.isModuleLevel }
        .mapNotNull { it.library }

      (projectLibraries + moduleLibraries)
        .mapNotNull { it as? LibraryEx }
        .mapNotNull { it.properties as? RepositoryLibraryProperties }
        .map { "${it.groupId}:${it.artifactId}" }
        .toSet()
    }
  }
}
