// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.openapi.externalSystem.settings.workspaceModel

import com.intellij.openapi.externalSystem.model.project.ExternalModuleBuildClasspathPojo
import com.intellij.openapi.externalSystem.model.project.ExternalProjectBuildClasspathPojo
import org.jetbrains.annotations.ApiStatus

fun getExternalProjectsBuildClasspathEntity(
  projectBuildClasspath: Map<String, ExternalProjectBuildClasspathPojo>
): ExternalProjectsBuildClasspathEntity.Builder {
  val classpathEntityMap = projectBuildClasspath.mapValues { getExternalProjectBuildClasspathEntity(it.value) }
  return ExternalProjectsBuildClasspathEntity.invoke(
    projectsBuildClasspath = classpathEntityMap,
    entitySource = ExternalProjectsBuildClasspathEntitySource
  )
}

fun getExternalProjectBuildClasspathPojo(entity: ExternalProjectsBuildClasspathEntity): Map<String, ExternalProjectBuildClasspathPojo> {
  return entity.projectsBuildClasspath
    .mapValues { getExternalProjectBuildClasspathPojo(it.value) }
}

private fun getExternalProjectBuildClasspathEntity(pojo: ExternalProjectBuildClasspathPojo): ExternalProjectBuildClasspathEntity {
  val moduleBuildClasspath = pojo.modulesBuildClasspath
    .mapValues {
      ExternalModuleBuildClasspathEntity(
        path = it.value.path,
        entries = it.value.entries
      )
    }
  return ExternalProjectBuildClasspathEntity(
    name = pojo.name,
    projectBuildClasspath = pojo.projectBuildClasspath,
    moduleBuildClasspath = moduleBuildClasspath
  )
}

private fun getExternalProjectBuildClasspathPojo(entity: ExternalProjectBuildClasspathEntity): ExternalProjectBuildClasspathPojo {
  return ExternalProjectBuildClasspathPojo(
    entity.name,
    entity.projectBuildClasspath,
    entity.moduleBuildClasspath
      .mapValues {
        ExternalModuleBuildClasspathPojo(
          it.value.path,
          it.value.entries
        )
      }
  )
}
