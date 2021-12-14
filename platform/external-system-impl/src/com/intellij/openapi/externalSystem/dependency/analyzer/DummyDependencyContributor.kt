// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.*
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency.Data.Artifact
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Dependency.Data.Module
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyContributor.Status.Omitted
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.LocalTimeCounter
import com.intellij.util.PathUtil

abstract class DummyDependencyContributor(private val project: Project) : DependencyContributor {

  @Suppress("HardCodedStringLiteral")
  private fun externalProject(externalProjectPath: String) =
    ExternalProject(externalProjectPath, PathUtil.getFileName(externalProjectPath))

  @Suppress("HardCodedStringLiteral")
  private fun scope(id: String) =
    Scope(id, StringUtil.toLowerCase(id), StringUtil.toTitleCase(id))

  override fun getExternalProjects() = listOf(
    externalProject(project.basePath!! + "/parent1"),
    externalProject(project.basePath!! + "/parent2"),
    externalProject(project.basePath!! + "/module"),
    externalProject(project.basePath!! + "/module" + LocalTimeCounter.currentTime())
  )

  override fun getDependencyScopes(externalProjectPath: String) = listOf(
    scope("compile"),
    scope("runtime"),
    scope("provided"),
    scope("test"),
    scope("system"),
    scope("import"),
    scope("scope" + LocalTimeCounter.currentTime())
  )

  private fun getRoot(externalProjectPath: String): Dependency {
    return createDependency(Module(PathUtil.getFileName(externalProjectPath)), scope("compile"), null)
  }

  private fun getDependencies(dependency: Dependency): List<Dependency> {
    return when (dependency.data.id) {
      "parent1" -> listOf(
        createDependency(Artifact("org.hamcrest", "hamcrest", "2.2"), scope("compile"), dependency),
        createDependency(Artifact("org.hamcrest", "hamcrest-core", "2.2"), scope("runtime"), dependency),
        createDependency(Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"), scope("provided"), dependency),
        createDependency(Artifact("org.junit.jupiter", "junit-jupiter-engine", "5.8.0"), scope("test"), dependency),
        createDependency(Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"), scope("system"), dependency),
        createDependency(Artifact("org.junit.platform", "junit-platform-engine", "1.8.0"), scope("import"), dependency)
      )
      "parent2" -> listOf(
        createDependency(Artifact("org.hamcrest", "hamcrest-core", "1.3"), scope("compile"), dependency)
      )
      "module" -> listOf(
        createDependency(Module("parent1"), scope("compile"), dependency),
        createDependency(Module("parent2"), scope("compile"), dependency)
      )
      "org.hamcrest:hamcrest-core:2.2" -> listOf(
        createDependency(Artifact("org.hamcrest", "hamcrest", "2.2"), scope("compile"), dependency)
      )
      "org.hamcrest:hamcrest-core:1.3" -> listOf(
        createDependency(Artifact("org.hamcrest", "hamcrest", "1.3"), scope("compile"), dependency)
      )
      "org.junit.jupiter:junit-jupiter-api:5.8.0" -> listOf(
        createDependency(Artifact("org.opentest4j", "opentest4j", "1.2.0"), scope("compile"), dependency),
        createDependency(Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"), scope("compile"), dependency),
        createDependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), scope("compile"), dependency)
      )
      "org.junit.jupiter:junit-jupiter-engine:5.8.0" -> listOf(
        createDependency(Artifact("org.junit.platform", "junit-platform-engine", "1.8.0"), scope("compile"), dependency),
        createDependency(Artifact("org.junit.jupiter", "junit-jupiter-api", "5.8.0"), scope("compile"), dependency),
        createDependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), scope("compile"), dependency)
      )
      "org.junit.platform:junit-platform-commons:1.8.0" -> listOf(
        createDependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), scope("compile"), dependency)
      )
      "org.junit.platform:junit-platform-engine:1.8.0" -> listOf(
        createDependency(Artifact("org.opentest4j", "opentest4j", "1.2.0"), scope("compile"), dependency),
        createDependency(Artifact("org.junit.platform", "junit-platform-commons", "1.8.0"), scope("compile"), dependency),
        createDependency(Artifact("org.apiguardian", "apiguardian-api", "1.1.2"), scope("compile"), dependency)
      )
      else -> emptyList()
    }
  }

  override fun getDependencies(externalProjectPath: String): List<Dependency> {
    val dependencies = ArrayList<Dependency>()
    val queue = ArrayDeque<Dependency>()
    queue.add(getRoot(externalProjectPath))
    while (queue.isNotEmpty()) {
      val dependency = queue.removeFirst()
      dependencies.add(dependency)
      getDependencies(dependency)
        .forEach(queue::addLast)
    }
    return dependencies
  }

  private fun getStatus(data: Dependency.Data, usage: Dependency?): List<Status> {
    when (data.id) {
      "org.hamcrest:hamcrest-core:1.3" ->
        if (matchesUsagePathPrefix(usage, "parent2", "module")) {
          return listOf(createVersionConflict("2.2"), Omitted)
        }
      "org.hamcrest:hamcrest:1.3" ->
        if (matchesUsagePathPrefix(usage, "org.hamcrest:hamcrest-core:1.3", "parent2", "module")) {
          return listOf(createVersionConflict("2.2"), Omitted)
        }
      "org.hamcrest:hamcrest-core:2.2" -> {
        if (matchesUsagePathPrefix(usage, "parent1", "module")) {
          return listOf(createVersionConflict("1.3"))
        }
        if (!matchesUsagePathPrefix(usage, "parent1")) {
          return listOf(Omitted)
        }
      }
      "org.hamcrest:hamcrest:2.2" -> {
        if (matchesUsagePathPrefix(usage, "org.hamcrest:hamcrest-core:2.2", "parent1", "module")) {
          return listOf(createVersionConflict("1.3"))
        }
        if (!matchesUsagePathPrefix(usage, "parent1")) {
          return listOf(Omitted)
        }
      }
      "org.junit.jupiter:junit-jupiter-api:5.8.0" ->
        if (!matchesUsagePathPrefix(usage, "parent1")) {
          return listOf(Omitted)
        }
      "org.junit.jupiter:junit-jupiter-engine:5.8.0" ->
        if (!matchesUsagePathPrefix(usage, "parent1")) {
          return listOf(Omitted)
        }
      "org.junit.platform:junit-platform-commons:1.8.0" ->
        if (!matchesUsagePathPrefix(usage, "parent1")) {
          return listOf(Omitted)
        }
      "org.junit.platform:junit-platform-engine:1.8.0" ->
        if (!matchesUsagePathPrefix(usage, "parent1")) {
          return listOf(Omitted)
        }
      "org.opentest4j:opentest4j:1.2.0" ->
        if (!matchesUsagePathPrefix(usage, "org.junit.jupiter:junit-jupiter-api:5.8.0", "parent1")) {
          return listOf(Omitted)
        }
      "org.apiguardian:apiguardian-api:1.1.2" ->
        if (!matchesUsagePathPrefix(usage, "org.junit.jupiter:junit-jupiter-api:5.8.0", "parent1")) {
          return listOf(Omitted)
        }
    }
    return emptyList()
  }

  private fun createDependency(data: Dependency.Data, scope: Scope, usage: Dependency?) =
    Dependency(data, scope, usage, getStatus(data, usage))

  private fun createVersionConflict(conflictedVersion: String) =
    Status.Warning(ExternalSystemBundle.message("external.system.dependency.analyzer.warning.version.conflict", conflictedVersion))

  private fun matchesUsagePathPrefix(dependency: Dependency?, vararg ids: String): Boolean {
    if (ids.isEmpty()) return true
    return dependency?.data?.id == ids.first() &&
           matchesUsagePathPrefix(dependency.parent, *ids.drop(1).toTypedArray())
  }

  private val Dependency.Data.id
    get() = when (this) {
      is Module -> name
      is Artifact -> "$groupId:$artifactId:$version"
    }
}