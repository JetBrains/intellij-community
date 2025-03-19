// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JpsModelFromTestData")
package org.jetbrains.jps.model.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import kotlin.io.path.*

class JpsProjectData private constructor(relativeProjectPath: String, externalConfigurationRelativePath: String?, testClass: Class<*>, 
                                         pathVariables: Map<String, String>) {
  val baseProjectDir: Path
  val project: JpsProject

  init {
    val homeDir = Path(PathManagerEx.getHomePath(testClass))
    val projectPath = homeDir.resolve(relativeProjectPath)
    require(projectPath.exists()) { "$projectPath doesn't exist" }
    baseProjectDir = if (projectPath.extension == "ipr") projectPath.parent else projectPath
    val externalConfigurationPath = externalConfigurationRelativePath?.let { homeDir.resolve(it) }
    project = JpsSerializationManager.getInstance().loadProject(projectPath, externalConfigurationPath, pathVariables, false)
  }

  fun getUrl(relativePath: String): String = JpsPathUtil.pathToUrl(baseProjectDir.resolve(relativePath).invariantSeparatorsPathString)
  
  fun resolvePath(relativePath: String): Path = baseProjectDir.resolve(relativePath)
  
  companion object {
    @JvmStatic
    @JvmOverloads
    fun loadFromTestData(
      relativeProjectPath: String, testClass: Class<*>,
      externalConfigurationRelativePath: String? = null,
      pathVariables: Map<String, String> = emptyMap(),
    ): JpsProjectData {
      return JpsProjectData(relativeProjectPath, externalConfigurationRelativePath, testClass, pathVariables)
    }
  }
}

@JvmOverloads
fun loadGlobalSettings(relativeOptionsPath: String, testClass: Class<*>, additionalPathVariables: Map<String, String> = emptyMap()): JpsModel {
  val homeDir = Path(PathManagerEx.getHomePath(testClass))
  val optionsPath = homeDir.resolve(relativeOptionsPath)
  require(optionsPath.exists()) { "$relativeOptionsPath doesn't exist" }
  val model = JpsElementFactory.getInstance().createModel()
  val configuration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
  for ((key, value) in additionalPathVariables.entries) {
    configuration.addPathVariable(key, value)
  }
  JpsGlobalSettingsLoading.loadGlobalSettings(model.global, optionsPath)
  return model
}

