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
package com.intellij.openapi.externalSystem

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.ExternalProjectSystemRegistry

private const val EXTERNAL_SYSTEM_MODULE_GROUP_KEY = "external.system.module.group"
private const val LINKED_PROJECT_PATH_KEY = "external.linked.project.path"
private const val LINKED_PROJECT_ID_KEY = "external.linked.project.id"
private const val EXTERNAL_SYSTEM_MODULE_TYPE_KEY = "external.system.module.type"
private const val EXTERNAL_SYSTEM_MODULE_VERSION_KEY = "external.system.module.version"
private const val ROOT_PROJECT_PATH_KEY = "external.root.project.path"

@Suppress("DEPRECATION")
class ExternalSystemModulePropertyManager(private val module: Module) {
  companion object {
    @JvmStatic
    fun getInstance(module: Module) = ModuleServiceManager.getService(module, ExternalSystemModulePropertyManager::class.java)!!
  }

  @Suppress("DEPRECATION")
  fun getExternalSystemId() = module.getOptionValue(EXTERNAL_SYSTEM_ID_KEY)

  fun getExternalModuleType() = module.getOptionValue(EXTERNAL_SYSTEM_MODULE_TYPE_KEY)

  fun getExternalModuleVersion() = module.getOptionValue(EXTERNAL_SYSTEM_MODULE_VERSION_KEY)

  fun getExternalModuleGroup() = module.getOptionValue(EXTERNAL_SYSTEM_MODULE_GROUP_KEY)

  fun getLinkedProjectId() = module.getOptionValue(LINKED_PROJECT_ID_KEY)

  fun getRootProjectPath() = module.getOptionValue(ROOT_PROJECT_PATH_KEY)

  fun getLinkedProjectPath() = module.getOptionValue(LINKED_PROJECT_PATH_KEY)

  @Suppress("DEPRECATION")
  fun isMavenized() = "true" == module.getOptionValue(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY)

  @Suppress("DEPRECATION")
  fun setMavenized(mavenized: Boolean) {
    module.setOption(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY, if (mavenized) "true" else null)

    if (!mavenized) {
      return
    }

    // clear external system API options
    // see com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService#setModuleOptions
    module.setOption(EXTERNAL_SYSTEM_ID_KEY, null)
    module.setOption(LINKED_PROJECT_PATH_KEY, null)
    module.setOption(ROOT_PROJECT_PATH_KEY, null)
    module.setOption(EXTERNAL_SYSTEM_MODULE_GROUP_KEY, null)
    module.setOption(EXTERNAL_SYSTEM_MODULE_VERSION_KEY, null)
  }

  fun unlinkExternalOptions() {
    module.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, null)
    module.setOption(LINKED_PROJECT_ID_KEY, null)
    module.setOption(LINKED_PROJECT_PATH_KEY, null)
    module.setOption(ROOT_PROJECT_PATH_KEY, null)
    module.setOption(EXTERNAL_SYSTEM_MODULE_GROUP_KEY, null)
    module.setOption(EXTERNAL_SYSTEM_MODULE_VERSION_KEY, null)
  }

  fun setExternalOptions(id: ProjectSystemId, moduleData: ModuleData, projectData: ProjectData?) {
    module.setOption(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY, id.toString())
    module.setOption(LINKED_PROJECT_ID_KEY, moduleData.id)
    module.setOption(LINKED_PROJECT_PATH_KEY, moduleData.linkedExternalProjectPath)
    module.setOption(ROOT_PROJECT_PATH_KEY, projectData?.linkedExternalProjectPath ?: "")

    module.setOption(EXTERNAL_SYSTEM_MODULE_GROUP_KEY, moduleData.group)
    module.setOption(EXTERNAL_SYSTEM_MODULE_VERSION_KEY, moduleData.version)

    // clear maven option
    module.setOption(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY, null)
  }

  fun setExternalId(id: ProjectSystemId) {
    module.setOption(EXTERNAL_SYSTEM_ID_KEY, id.id)
  }

  fun seExternalModuleType(type: String?) {
    module.setOption(EXTERNAL_SYSTEM_MODULE_TYPE_KEY, type)
  }

  fun setLinkedProjectId(projectId: String) {
    module.setOption(LINKED_PROJECT_ID_KEY, projectId)
  }
}