// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectStoreFactory

abstract class ProjectStoreFactoryBase : ProjectStoreFactory {
  final override fun createDefaultProjectStore(project: Project): IComponentStore = DefaultProjectStoreImpl(project)
}

internal class PlatformLangProjectStoreFactory : ProjectStoreFactoryBase() {
  override fun createStore(project: Project): IProjectStore {
    LOG.assertTrue(!project.isDefault)
    return ProjectWithModuleStoreImpl(project)
  }
}

internal class PlatformProjectStoreFactory : ProjectStoreFactoryBase() {
  override fun createStore(project: Project): IProjectStore {
    LOG.assertTrue(!project.isDefault)
    return ProjectStoreImpl(project)
  }
}
