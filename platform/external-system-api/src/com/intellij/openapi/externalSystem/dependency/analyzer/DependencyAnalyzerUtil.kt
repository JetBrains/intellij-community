// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.module.Module
import com.intellij.openapi.externalSystem.dependency.analyzer.DependencyAnalyzerDependency as Dependency


private fun getDependencyData(coordinates: UnifiedCoordinates): Dependency.Data {
  val groupId = coordinates.groupId ?: ""
  val artifactId = coordinates.artifactId ?: ""
  val version = coordinates.version ?: ""
  return DAArtifact(groupId, artifactId, version)
}

fun DependencyAnalyzerView.setSelectedDependency(module: Module, coordinates: UnifiedCoordinates, scope: String? = null) {
  val data = getDependencyData(coordinates)
  when (scope) {
    null -> setSelectedDependency(module, data)
    else -> setSelectedDependency(module, data, scope)
  }
}

fun DependencyAnalyzerView.setSelectedDependency(module: Module, unifiedPath: List<UnifiedCoordinates>, scope: String? = null) {
  val path = unifiedPath.map { getDependencyData(it) }
  when (scope) {
    null -> setSelectedDependency(module, path)
    else -> setSelectedDependency(module, path, scope)
  }
}

fun DependencyAnalyzerView.setSelectedDependency(module: Module, dependency: UnifiedDependency) {
  setSelectedDependency(module, dependency.coordinates, dependency.scope)
}

fun DependencyAnalyzerView.setSelectedDependency(module: Module, dependency: UnifiedDependency, path: List<UnifiedCoordinates>) {
  setSelectedDependency(module, listOf(dependency.coordinates) + path, dependency.scope)
}