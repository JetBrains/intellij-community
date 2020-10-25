// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.google.gson.Gson
import com.intellij.analysis.AnalysisScope
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.scope.packageSet.*
import com.intellij.util.io.exists
import com.intellij.util.io.isAncestor
import java.nio.file.Path
import java.nio.file.Paths

private const val QODANA_GLOBAL_SCOPE = "qodana.global"

class QodanaConfig(val profile: String = "", val excludes: List<ExclusionFilter> = emptyList()) {
  companion object {
    fun read(path: Path): QodanaConfig {
      if (!path.exists()) return QodanaConfig("", emptyList())
      return Gson().fromJson(path.toFile().readText(), QodanaConfig::class.java)
    }
  }

  fun getGlobalScope(project: Project, baseScope: AnalysisScope): AnalysisScope {
    val sets = excludes.filter { it.inspections.isEmpty() }.map { it.getScope(project) }.toTypedArray()
    if (sets.isEmpty()) return baseScope
    val scopeManager = NamedScopeManager.getInstance(project)
    val globalScope = NamedScope(QODANA_GLOBAL_SCOPE, ComplementPackageSet(UnionPackageSet.create(*sets)))
    scopeManager.addScope(globalScope)
    return AnalysisScope(GlobalSearchScopesCore.filterScope(project, globalScope), project)
  }

  fun updateScope(profile: InspectionProfileImpl, project: Project) {
    val inspections = excludes.flatMap { it.inspections }.distinct()
    inspections.forEach {
      val tools = profile.getTools(it, project)
      val scope = getExcludedScope(it, project)
      tools.addTool(scope, tools.tool, false, HighlightDisplayLevel.DO_NOT_SHOW)
    }
  }

  fun getExcludedScope(inspectionId: String, project: Project): NamedScope {
    val sets = excludes.filter { it.inspections.contains(inspectionId) }.map { it.getScope(project) }.toTypedArray()
    val namedScope = NamedScope("qodana.excluded.$inspectionId", UnionPackageSet.create(*sets))
    NamedScopeManager.getInstance(project).addScope(namedScope)
    return namedScope
  }
}

data class ExclusionFilter(
  val paths: List<String> = emptyList(),
  val patterns: List<String> = emptyList(),
  val inspections: List<String> = emptyList(),
) {
  fun getScope(project: Project): PackageSet {
    val sets = mutableListOf<PackageSet>()

    val packageSetFactory = PackageSetFactory.getInstance()
    sets.addAll(patterns.map { packageSetFactory.compile(it) })

    if (paths.isNotEmpty()) {
      val pathsSet = object : AbstractPackageSet(paths.joinToString()) {
        val absolutePaths = paths.map { Paths.get(project.basePath ?: "", it).normalize().toAbsolutePath() }
        override fun contains(file: VirtualFile, holder: NamedScopesHolder?): Boolean {
          val path = Paths.get(file.path)
          return absolutePaths.firstOrNull { it.isAncestor(path) } != null
        }
      }

      sets.add(pathsSet)
    }
    return UnionPackageSet.create(*sets.toTypedArray())
  }
}
