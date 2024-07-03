// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

abstract class JavaCodeStyleSettingsProjectMigrationManagerBase : SimplePersistentStateComponent<MigrationState>(MigrationState())

@Service(Service.Level.PROJECT)
@State(name = "JavaCodeStyleSettingsProjectMigration", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class JavaCodeStyleSettingsProjectMigrationManager : JavaCodeStyleSettingsProjectMigrationManagerBase() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<JavaCodeStyleSettingsProjectMigrationManager>()
  }
}


@Service(Service.Level.APP)
@State(name = "JavaCodeStyleSettingsApplicationMigrationManager", storages = [Storage("java.code.style.migration.xml")])
class JavaCodeStyleSettingsApplicationMigrationManager : JavaCodeStyleSettingsProjectMigrationManagerBase() {
  companion object {
    @JvmStatic
    fun getInstance() = ApplicationManager.getApplication().service<JavaCodeStyleSettingsApplicationMigrationManager>()
  }
}

class MigrationState : BaseState() {
  var areSchemesMigrated by property(false)
}