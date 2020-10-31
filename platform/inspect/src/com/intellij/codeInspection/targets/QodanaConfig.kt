// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionApplication
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.scope.packageSet.*
import com.intellij.util.io.exists
import com.intellij.util.io.isAncestor
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private const val QODANA_GLOBAL_SCOPE = "qodana.global"
private const val QODANA_CONFIG_FILENAME = "qodana.json"
private const val DEFAULT_QODANA_PROFILE = "qodana.recommended"


class QodanaConfig(val profile: QodanaProfile = QodanaProfile(), val excludes: List<ExclusionFilter> = emptyList()) {
  companion object {
    @JvmField
    val EMPTY = QodanaConfig()

    fun load(projectPath: Path, application: InspectionApplication): QodanaConfig {
      val path = projectPath.resolve(QODANA_CONFIG_FILENAME)
      val qodanaConfig = if (!path.exists()) {
        return EMPTY
      }
      else {
        Gson().fromJson(path.toFile().readText(), QodanaConfig::class.java)
      }

      if (qodanaConfig.profile.name.isEmpty() && qodanaConfig.profile.path.isEmpty()) {
        if (application.myProfileName != null) {
          return QodanaConfig(QodanaProfile("", application.myProfileName), qodanaConfig.excludes)
        }
        if (application.myProfilePath != null) {
          return QodanaConfig(QodanaProfile("", application.myProfilePath), qodanaConfig.excludes)
        }
        return QodanaConfig(QodanaProfile(DEFAULT_QODANA_PROFILE, application.myProfilePath), qodanaConfig.excludes)
      } else {
        return qodanaConfig
      }
    }
  }

  fun write() {
    val logTarget = Paths.get(PathManager.getLogPath(), QODANA_CONFIG_FILENAME)
    val gson = GsonBuilder().setPrettyPrinting().create()
    val writer = OutputStreamWriter(FileOutputStream(logTarget.toFile()), Charsets.UTF_8)
    writer.use { gson.toJson(this, writer) }
  }

  fun getGlobalScope(project: Project): GlobalSearchScope? {
    val sets = excludes.filter { it.inspections.isEmpty() }.map { it.getScope(project) }.toTypedArray()
    if (sets.isEmpty()) return null
    val scopeManager = NamedScopeManager.getInstance(project)
    val globalScope = NamedScope(QODANA_GLOBAL_SCOPE, ComplementPackageSet(UnionPackageSet.create(*sets)))
    scopeManager.addScope(globalScope)
    return GlobalSearchScopesCore.filterScope(project, globalScope)
  }

  fun updateToolsScopes(profile: InspectionProfileImpl, project: Project) {
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

data class QodanaProfile(val path: String = "", val name: String = "")