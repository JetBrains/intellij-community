/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.facet.Facet
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.FacetManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Created by Nikita.Skvortsov
 * date: 12.09.2017.
 */

class FacetConfigurationHandler : ConfigurationHandler {

  companion object {
    val LOG: Logger = Logger.getInstance(FacetConfigurationHandler::class.java)
  }

  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val modifiableModel = modelsProvider.getModifiableFacetModel(module)
    configuration.eachFacet { typeName, name, cfg ->
      FacetHandlerExtensionManager.handlerForType(
        typeName)?.process(module, name, cfg, FacetManager.getInstance(module))
    }.forEach {
      modifiableModel.addFacet(it)
    }
  }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {}

  private fun ConfigurationData.eachFacet(f: (String, String, Map<String, *>) -> Collection<Facet<out FacetConfiguration>>?):
    List<Facet<out FacetConfiguration>> {
    val runCfgMap = find("facets")

    if (runCfgMap !is List<*>) { return emptyList() } else {
      return runCfgMap.map { cfg ->

        if (cfg !is Map<*, *>) {
          RunConfigurationHandler.LOG.warn("unexpected value type in facets map: ${cfg?.javaClass?.name}, skipping")
          return@map null
        }

        val name = cfg["name"]
        if (name !is String) {
          RunConfigurationHandler.LOG.warn("unexpected key type in facets map: ${name?.javaClass?.name}, skipping")
          return@map null
        }

        val typeName = cfg["type"] as? String ?: name
        try {
          return@map f(typeName, name, cfg as Map<String, *>)
        }
        catch (e: Exception) {
          RunConfigurationHandler.LOG.warn("Error occurred when importing run configuration ${name}: ${e.message}", e)
        }

        return@map null
      }
        .filterNotNull()
        .flatten()
    }
  }
}



class FacetHandlerExtensionManager {
  companion object {
    fun handlerForType(typeName: String): FacetConfigurationImporter<out Facet<out FacetConfiguration>>? =
      Extensions.getExtensions(
        FacetConfigurationImporter.EP_NAME).firstOrNull { it.canHandle(typeName) }
  }
}