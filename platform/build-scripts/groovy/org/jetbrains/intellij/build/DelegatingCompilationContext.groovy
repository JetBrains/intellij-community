// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.BundledRuntime
import org.jetbrains.intellij.build.impl.DependenciesProperties
import org.jetbrains.intellij.build.impl.JpsCompilationData
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule

import java.nio.file.Path

abstract class DelegatingCompilationContext implements CompilationContext {
  private final CompilationContext delegate

  DelegatingCompilationContext(CompilationContext delegate) {
    this.delegate = delegate
  }

  @Override
  AntBuilder getAnt() {
    return delegate.getAnt()
  }

  @Override
  BuildOptions getOptions() {
    return delegate.getOptions()
  }

  @Override
  BuildMessages getMessages() {
    return delegate.getMessages()
  }

  @Override
  BuildPaths getPaths() {
    return delegate.getPaths()
  }

  @Override
  JpsProject getProject() {
    return delegate.getProject()
  }

  @Override
  JpsModel getProjectModel() {
    return delegate.getProjectModel()
  }

  @Override
  JpsCompilationData getCompilationData() {
    return delegate.getCompilationData()
  }

  @Override
  File getProjectOutputDirectory() {
    return delegate.getProjectOutputDirectory()
  }

  @Override
  JpsModule findRequiredModule(String name) {
    return delegate.findRequiredModule(name)
  }

  @Override
  JpsModule findModule(String name) {
    return delegate.findModule(name)
  }

  @Override
  String getOldModuleName(String newName) {
    return delegate.getOldModuleName(newName)
  }

  @Override
  Path getModuleOutputDir(JpsModule module) {
    return delegate.getModuleOutputDir(module)
  }

  @Override
  String getModuleTestsOutputPath(JpsModule module) {
    return delegate.getModuleTestsOutputPath(module)
  }

  @Override
  List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests) {
    return delegate.getModuleRuntimeClasspath(module, forTests)
  }

  @Override
  void notifyArtifactBuilt(String artifactPath) {
    //noinspection GrDeprecatedAPIUsage
    delegate.notifyArtifactBuilt(artifactPath)
  }

  @Override
  void notifyArtifactWasBuilt(Path artifactPath) {
    delegate.notifyArtifactWasBuilt(artifactPath)
  }

  @Override
  DependenciesProperties getDependenciesProperties() {
    return delegate.getDependenciesProperties()
  }

  @Override
  BundledRuntime getBundledRuntime() {
    return delegate.getBundledRuntime()
  }
}
