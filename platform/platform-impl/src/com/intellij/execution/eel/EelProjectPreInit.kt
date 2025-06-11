// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.platform.eel.provider.EelInitialization

/**
 * During the process of project initialization, the IDE interacts with the file system where the project is located on.
 * It happens, for example, during the loading of Workspace Model cache.
 * Before the first such interaction, we must ensure that the file system is accessible to the IDE.
 * Since Eel is responsible for it, it must run *very* early.
 *
 * On the other hand, we should not access non-local environments excessively. The process of initialization may require IO requests
 * which could severely hinder the performance of IDE startup.
 * It means that the suitable way to initialize Eel is right before the initialization of a project (when we can decide if we should access the environment),
 * and not earlier.
 */
private class EelProjectPreInit : InitProjectActivity {
  override suspend fun run(project: Project) {
    EelInitialization.runEelInitialization(project)
  }
}