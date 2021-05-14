// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.targets

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeHighlighting.HighlightDisplayLevel.*
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.ToolsImpl
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.scope.ProjectFilesScope
import com.intellij.psi.search.scope.packageSet.*
import com.intellij.util.io.exists
import com.intellij.util.io.isAncestor
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private const val QODANA_GLOBAL_SCOPE = "qodana.global"
const val QODANA_CONFIG_FILENAME = "qodana.yaml"
const val DEFAULT_QODANA_PROFILE = "qodana.recommended"
private const val CONFIG_VERSION = "1.0"
private const val CONFIG_ALL_INSPECTIONS = "All"

private const val DEFAULT_FAIL_THRESHOLD = -1
const val DEFAULT_FAIL_EXIT_CODE = 255
const val DEFAULT_STOP_THRESHOLD = -1
typealias InspectScopes = List<InspectScope>

data class QodanaProfile(
  var path: String = "",
  var name: String = ""
)

data class InspectScope(var name: String = "", var paths: List<String> = emptyList(), var patterns: List<String> = emptyList()) {
  fun getScope(project: Project): PackageSet? {
    val sets = mutableListOf<PackageSet>()

    val packageSetFactory = PackageSetFactory.getInstance()
    sets.addAll(patterns.map { packageSetFactory.compile(it) })

    if (paths.isNotEmpty()) {
      val pathsSet = object : AbstractPackageSet(paths.joinToString()) {
        val absolutePaths = paths.map { Paths.get(project.basePath ?: "", it).normalize().toAbsolutePath() }
        override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
          val path = Paths.get(file.path)
          return absolutePaths.firstOrNull { it.isAncestor(path) } != null
        }
      }

      sets.add(pathsSet)
    }
    return if (sets.isEmpty()) return null
    else UnionPackageSet.create(*sets.toTypedArray())

  }
}

class QodanaConfig(var version: String = CONFIG_VERSION,
                   var profile: QodanaProfile = QodanaProfile(),
                   var exclude: InspectScopes = emptyList(),
                   var include: InspectScopes = emptyList(),
                   var criticalSeverity: InspectScopes = emptyList(),
                   var highSeverity: InspectScopes = emptyList(),
                   var moderateSeverity: InspectScopes = emptyList(),
                   var failThreshold: Int = DEFAULT_FAIL_THRESHOLD,
                   var stopThreshold: Int = DEFAULT_STOP_THRESHOLD) {
  companion object {
    @JvmField
    val EMPTY = QodanaConfig()

    fun load(projectPath: Path): QodanaConfig {
      val path = projectPath.resolve(QODANA_CONFIG_FILENAME)

      val yaml = Yaml()
      return if (!path.exists()) {
        EMPTY
      }
      else {
        InputStreamReader(Files.newInputStream(path)).use {
          yaml.loadAs(it, QodanaConfig::class.java)
        }
      }
    }
  }

  fun isAboveStopThreshold(count: Int): Boolean {
    return stopThreshold in 0 until count
  }

  fun copyToLog(projectPath: Path) {
    val path = projectPath.resolve(QODANA_CONFIG_FILENAME)
    if (!path.exists()) return
    Files.copy(path, Paths.get(PathManager.getLogPath(), QODANA_CONFIG_FILENAME), StandardCopyOption.REPLACE_EXISTING)
  }

  fun getGlobalScope(project: Project): GlobalSearchScope? {
    val set = exclude.find { it.name == CONFIG_ALL_INSPECTIONS }?.getScope(project) ?: return null
    val globalScope = createScope(QODANA_GLOBAL_SCOPE, ComplementPackageSet(set), project)
    return GlobalSearchScopesCore.filterScope(project, globalScope)
  }

  fun updateToolsScopes(profile: InspectionProfileImpl, project: Project) {

    val displayLevels = mapOf(
      exclude to { DO_NOT_SHOW },
      include to { t: ToolsImpl -> t.level },
      moderateSeverity to { WEAK_WARNING },
      highSeverity to { WARNING },
      criticalSeverity to { ERROR }
    )

    displayLevels.forEach { (scopes, levelSupplier) ->
      scopes.forEach {
        val tools = profile.getToolsOrNull(it.name, project)
        if (tools != null) {
          val level = levelSupplier.invoke(tools)
          val scope = createScope("qodana.$level.${it.name}", it.getScope(project), project)
          if (!tools.isEnabled) {
            disableAllScopes(tools)
            tools.isEnabled = true
          }
          tools.addTool(scope, tools.tool, true, level)
        }
      }
    }
  }

  private fun disableAllScopes(tools: ToolsImpl) {
    tools.tools.forEach {
      it.isEnabled = false
    }
  }

  fun createScope(scopeName: String, set: PackageSet?, project: Project): NamedScope {
    if (set == null) return ProjectFilesScope.INSTANCE
    val namedScope = NamedScope(scopeName, set)
    NamedScopeManager.getInstance(project).addScope(namedScope)
    return namedScope
  }
}
