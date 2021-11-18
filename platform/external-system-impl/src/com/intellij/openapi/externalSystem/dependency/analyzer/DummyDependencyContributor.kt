// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.*
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency.Data.Artifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency.Data.Module
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.InspectionResult.*
import com.intellij.openapi.project.Project
import com.intellij.util.LocalTimeCounter
import com.intellij.util.PathUtil

abstract class DummyDependencyContributor(private val project: Project) : DependencyContributor {

  override fun getExternalProjectPaths() = listOf(
    project.basePath!! + "/parent1",
    project.basePath!! + "/parent2",
    project.basePath!! + "/module",
    project.basePath!! + "/module" + LocalTimeCounter.currentTime()
  )

  override fun getExternalProjectName(externalProjectPath: String): String {
    return PathUtil.getFileName(externalProjectPath)
  }

  private fun getRoot(externalProjectPath: String): Dependency {
    return Dependency(Module(PathUtil.getFileName(externalProjectPath)), null)
  }

  private fun getDependencies(dependency: Dependency): List<Dependency> {
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

  override fun getDependencyGroups(externalProjectPath: String): List<DependencyGroup> {
    val dependencies = ArrayList<Dependency>()
    val queue = ArrayDeque<Dependency>()
    queue.add(getRoot(externalProjectPath))
    while (queue.isNotEmpty()) {
      val dependency = queue.removeFirst()
      dependencies.add(dependency)
      getDependencies(dependency)
        .forEach(queue::addLast)
    }
    val groups = dependencies.groupBy {
      when (val data = it.data) {
        is Module -> data.name
        is Artifact -> data.groupId + ":" + data.artifactId
      }
    }
    return groups.values.map { DependencyGroup(it.first().data, it) }
  }

  override fun getDependencyScopes(externalProjectPath: String): List<String> {
    return listOf(
      "compile",
      "runtime",
      "provided",
      "test",
      "system",
      "import",
      "scope" + LocalTimeCounter.currentTime()
    )
  }

  override fun getDependencyScope(externalProjectPath: String, dependency: Dependency): String {
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

  override fun getInspectionResult(externalProjectPath: String, dependency: Dependency): List<InspectionResult> {
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