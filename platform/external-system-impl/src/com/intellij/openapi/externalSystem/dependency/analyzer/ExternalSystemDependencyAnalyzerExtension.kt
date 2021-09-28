// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil


class ExternalSystemDependencyAnalyzerExtension : DependencyAnalyzerExtension {
  override fun getContributor(project: Project, systemId: ProjectSystemId): DependenciesContributor? {
    val manager = ExternalSystemApiUtil.getManager(systemId) ?: return null
    return Contributor(project, manager)
  }

  private class Contributor(val project: Project, val manager: ExternalSystemManager<*, *, *, *, *>) : DependenciesContributor {
    override fun getDependencyScopes(projectId: ExternalSystemProjectId): List<String> {
      return listOf("compile", "provided", "runtime", "test", "system", "import")
    }

    override fun getProjectIds(): List<ExternalSystemProjectId> {
      val projectIds = ArrayList<ExternalSystemProjectId>()
      val settings = manager.settingsProvider.`fun`(project)
      for (projectSettings in settings.linkedProjectsSettings) {
        projectIds.add(ExternalSystemProjectId(manager.systemId, projectSettings.externalProjectPath))
      }
      return projectIds
    }

    override fun getProjectName(projectId: ExternalSystemProjectId): String {
      return PathUtil.getFileName(projectId.externalProjectPath)
    }

    override fun getDependencies(projectId: ExternalSystemProjectId) = listOf(
      Module(project.name),
      Artifact(Artifact.Coordinates("org.hamcrest", "hamcrest", "2.2"), "compile"),
      Artifact(Artifact.Coordinates("org.hamcrest", "hamcrest-core", "2.2"), "runtime"),
      Artifact(Artifact.Coordinates("org.junit.jupiter", "junit-jupiter-api", "5.7.0"), "provided"),
      Artifact(Artifact.Coordinates("org.junit.jupiter", "junit-jupiter-engine", "5.7.0"), "test"),
      Artifact(Artifact.Coordinates("org.junit.platform", "junit-platform-commons", "1.7.0"), "system"),
      Artifact(Artifact.Coordinates("org.junit.platform", "junit-platform-engine", "1.7.0"), "import"),
      Artifact(Artifact.Coordinates("org.opentest4j", "opentest4j", "1.2.0"), "test")
    )

    override fun getUsages(projectId: ExternalSystemProjectId, dependency: DependencyData): List<DependencyData> {
      when (dependency) {
        is Artifact -> {
          when (dependency.coordinate.artifactId) {
            "junit-platform-commons" -> return listOf(
              Artifact(Artifact.Coordinates("org.junit.jupiter", "junit-jupiter-api", "5.7.0"), "provided"),
              Artifact(Artifact.Coordinates("org.junit.platform", "junit-platform-engine", "1.7.0"), "import")
            )
            "junit-jupiter-api" -> return listOf(
              Artifact(Artifact.Coordinates("org.junit.jupiter", "junit-jupiter-engine", "5.7.0"), "test")
            )
            "opentest4j" -> return listOf(
              Module(project.name)
            )
          }
        }
      }
      return emptyList()
    }
  }

  private data class Module(
    override val moduleName: String
  ) : DependencyData.Module

  private data class Artifact(
    override val coordinate: DependencyData.Artifact.Coordinates,
    override val scope: String
  ) : DependencyData.Artifact {

    data class Coordinates(
      override val groupId: String,
      override val artifactId: String,
      override val version: String
    ) : DependencyData.Artifact.Coordinates
  }
}