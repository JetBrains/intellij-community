// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.Dependency
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.Dependency.Data.Artifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.Dependency.Data.Module
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.InspectionResult
import com.intellij.openapi.externalSystem.dependency.analyzer.DependenciesContributor.InspectionResult.*
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil

class DummyDependenciesContributor(private val project: Project) : DependenciesContributor {
  override fun getProjectName(projectId: ExternalSystemProjectId): String {
    return PathUtil.getFileName(projectId.externalProjectPath)
  }

  override fun getProjectIds() = listOf(
    ExternalSystemProjectId(ProjectSystemId("DUMMY"), project.basePath!! + "/parent1"),
    ExternalSystemProjectId(ProjectSystemId("DUMMY"), project.basePath!! + "/parent2"),
    ExternalSystemProjectId(ProjectSystemId("DUMMY"), project.basePath!! + "/module")
  )

  override fun getRoot(projectId: ExternalSystemProjectId): Dependency {
    return Dependency(Module(PathUtil.getFileName(projectId.externalProjectPath)), null)
  }

  override fun getDependencies(projectId: ExternalSystemProjectId, dependency: Dependency): List<Dependency> {
    return when (dependency.data.id) {
      "parent1" -> listOf(
        Dependency(Artifact("org.hamcrest", "hamcrest", "2.2"), dependency),
        Dependency(Artifact("org.hamcrest", "hamcrest-core", "2.2"), dependency),
        Dependency(Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"), dependency),
        Dependency(Artifact("org.junit.jupiter", "junit-jupiter-engine", "5.8.0"), dependency),
        Dependency(Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"), dependency),
        Dependency(Artifact("org.junit.platform", "junit-platform-engine", "1.8.0"), dependency)
      )
      "parent2" -> listOf(
        Dependency(Artifact("org.hamcrest", "hamcrest-core", "1.3"), dependency)
      )
      "module" -> listOf(
        Dependency(Module("parent1"), dependency),
        Dependency(Module("parent2"), dependency)
      )
      "org.hamcrest:hamcrest-core:2.2" -> listOf(
        Dependency(Artifact("org.hamcrest", "hamcrest", "2.2"), dependency)
      )
      "org.hamcrest:hamcrest-core:1.3" -> listOf(
        Dependency(Artifact("org.hamcrest", "hamcrest", "1.3"), dependency)
      )
      "org.junit.jupiter:junit-jupiter-api:5.8.0" -> listOf(
        Dependency(Artifact("org.opentest4j", "opentest4j", "1.2.0"), dependency),
        Dependency(Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"), dependency),
        Dependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), dependency)
      )
      "org.junit.jupiter:junit-jupiter-engine:5.8.0" -> listOf(
        Dependency(Artifact("org.junit.platform", "junit-platform-engine", "1.8.0"), dependency),
        Dependency(Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"), dependency),
        Dependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), dependency)
      )
      "org.junit.platform:junit-platform-commons:1.8.0" -> listOf(
        Dependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), dependency)
      )
      "org.junit.platform:junit-platform-engine:1.8.0" -> listOf(
        Dependency(Artifact("org.opentest4j", "opentest4j", "1.2.0"), dependency),
        Dependency(Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"), dependency),
        Dependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), dependency)
      )
      else -> emptyList()
    }
  }

  override fun getVariances(projectId: ExternalSystemProjectId, dependency: Dependency): List<Dependency> {
    val result = ArrayList<Dependency>()
    val queue = ArrayDeque<List<Dependency.Data>>()
    for (variance in getVariances(projectId, dependency.data)) {
      queue.addLast(listOf(variance))
    }
    while (queue.isNotEmpty()) {
      val path = queue.removeFirst()
      val usages = getUsages(projectId, path.last())
      for (usage in usages) {
        queue.add(path + usage)
      }
      if (usages.isEmpty()) {
        var variance: Dependency? = null
        for (data in path.reversed()) {
          variance = Dependency(data, variance)
        }
        result.add(variance!!)
      }
    }
    return result
  }

  private fun getVariances(projectId: ExternalSystemProjectId, dependency: Dependency.Data): List<Dependency.Data> {
    if (getProjectName(projectId) == "module") {
      when (dependency.id) {
        "org.hamcrest:hamcrest:2.2", "org.hamcrest:hamcrest:1.3" ->
          return listOf(
            Artifact("org.hamcrest", "hamcrest", "2.2"),
            Artifact("org.hamcrest", "hamcrest", "1.3")
          )
        "org.hamcrest:hamcrest-core:2.2", "org.hamcrest:hamcrest-core:1.3" ->
          return listOf(
            Artifact("org.hamcrest", "hamcrest-core", "2.2"),
            Artifact("org.hamcrest", "hamcrest-core", "1.3")
          )
      }
    }
    return listOf(dependency)
  }

  private fun getUsages(projectId: ExternalSystemProjectId, dependency: Dependency.Data): List<Dependency.Data> {
    return when (dependency.id) {
      "parent1" -> when (getProjectName(projectId)) {
        "module" -> listOf(Module("module"))
        else -> emptyList()
      }
      "parent2" -> when (getProjectName(projectId)) {
        "module" -> listOf(Module("module"))
        else -> emptyList()
      }
      "org.hamcrest:hamcrest:2.2" -> listOf(
        Module("parent1"),
        Artifact("org.hamcrest", "hamcrest-core", "2.2")
      )
      "org.hamcrest:hamcrest:1.3" -> listOf(
        Artifact("org.hamcrest", "hamcrest-core", "1.3")
      )
      "org.hamcrest:hamcrest-core:2.2" -> listOf(
        Module("parent1")
      )
      "org.hamcrest:hamcrest-core:1.3" -> listOf(
        Module("parent2")
      )
      "org.opentest4j:opentest4j:1.2.0" -> listOf(
        Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"),
        Artifact("org.junit.platform", "junit-platform-engine", "1.8.0")
      )
      "org.apiguardian:apiguardian-api:1.1.2" -> listOf(
        Module("parent1"),
        Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"),
        Artifact("org.junit.jupiter", "junit-jupiter-engine", "5.8.0"),
        Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"),
        Artifact("org.junit.platform", "junit-platform-engine", "1.8.0")
      )
      "org.junit.jupiter:junit-jupiter-api:5.8.0" -> listOf(
        Module("parent1"),
        Artifact("org.junit.jupiter", "junit-jupiter-engine", "5.8.0")
      )
      "org.junit.jupiter:junit-jupiter-engine:5.8.0" -> listOf(
        Module("parent1")
      )
      "org.junit.platform:junit-platform-commons:1.8.0" -> listOf(
        Module("parent1"),
        Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"),
        Artifact("org.junit.platform", "junit-platform-engine", "1.8.0")
      )
      "org.junit.platform:junit-platform-engine:1.8.0" -> listOf(
        Module("parent1"),
        Artifact("org.junit.jupiter", "junit-jupiter-engine", "5.8.0")
      )
      else -> emptyList()
    }
  }

  override fun getDependencyScopes(projectId: ExternalSystemProjectId): List<String> {
    return listOf("compile", "runtime", "provided", "test", "system", "import")
  }

  override fun getDependencyScope(projectId: ExternalSystemProjectId, dependency: Dependency): String {
    return when (dependency.data.id) {
      "org.hamcrest:hamcrest-core:1.3" -> "compile"
      "org.hamcrest:hamcrest:2.2" -> "compile"
      "org.hamcrest:hamcrest-core:2.2" -> "runtime"
      "org.junit.jupiter:junit-jupiter-api:5.8.0" -> "provided"
      "org.junit.jupiter:junit-jupiter-engine:5.8.0" -> "test"
      "org.junit.platform:junit-platform-commons:1.8.0" -> "system"
      "org.junit.platform:junit-platform-engine:1.8.0" -> "import"
      "org.opentest4j:opentest4j:1.2.0" -> "compile"
      "org.apiguardian:apiguardian-api:1.1.2" -> "compile"
      else -> "compile"
    }
  }

  override fun getInspectionResult(projectId: ExternalSystemProjectId, dependency: Dependency): List<InspectionResult> {
    when (dependency.data.id) {
      "org.hamcrest:hamcrest-core:1.3" ->
        if (matchesUsagePathPrefix(dependency.usage, "parent2", "module")) {
          return listOf(VersionConflict(Artifact("org.hamcrest", "hamcrest-core", "2.2")), Omitted)
        }
      "org.hamcrest:hamcrest:1.3" ->
        if (matchesUsagePathPrefix(dependency.usage, "org.hamcrest:hamcrest-core:1.3", "parent2", "module")) {
          return listOf(VersionConflict(Artifact("org.hamcrest", "hamcrest", "2.2")), Omitted)
        }
      "org.hamcrest:hamcrest-core:2.2" -> {
        if (matchesUsagePathPrefix(dependency.usage, "parent1", "module")) {
          return listOf(VersionConflict(Artifact("org.hamcrest", "hamcrest-core", "1.3")))
        }
        if (!matchesUsagePathPrefix(dependency.usage, "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      }
      "org.hamcrest:hamcrest:2.2" -> {
        if (matchesUsagePathPrefix(dependency.usage, "org.hamcrest:hamcrest-core:2.2", "parent1", "module")) {
          return listOf(VersionConflict(Artifact("org.hamcrest", "hamcrest", "1.3")))
        }
        if (!matchesUsagePathPrefix(dependency.usage, "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      }
      "org.junit.jupiter:junit-jupiter-api:5.8.0" ->
        if (!matchesUsagePathPrefix(dependency.usage, "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      "org.junit.jupiter:junit-jupiter-engine:5.8.0" ->
        if (!matchesUsagePathPrefix(dependency.usage, "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      "org.junit.platform:junit-platform-commons:1.8.0" ->
        if (!matchesUsagePathPrefix(dependency.usage, "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      "org.junit.platform:junit-platform-engine:1.8.0" ->
        if (!matchesUsagePathPrefix(dependency.usage, "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      "org.opentest4j:opentest4j:1.2.0" ->
        if (!matchesUsagePathPrefix(dependency.usage, "org.junit.jupiter:junit-jupiter-api:5.8.0", "parent1")) {
          return listOf(Duplicate, Omitted)
        }
      "org.apiguardian:apiguardian-api:1.1.2" ->
        if (!matchesUsagePathPrefix(dependency.usage, "org.junit.jupiter:junit-jupiter-api:5.8.0", "parent1")) {
          return listOf(Duplicate, Omitted)
        }
    }
    return emptyList()
  }

  private fun matchesUsagePathPrefix(dependency: Dependency?, vararg ids: String): Boolean {
    if (ids.isEmpty()) return true
    return dependency?.data?.id == ids.first() &&
           matchesUsagePathPrefix(dependency.usage, *ids.drop(1).toTypedArray())
  }

  private val Dependency.Data.id
    get() = when (this) {
      is Module -> name
      is Artifact -> "$groupId:$artifactId:$version"
    }
}