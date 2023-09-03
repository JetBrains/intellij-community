// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.warmup

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Project warm-up is a set of activities that help to prepare an IDE for being opened in a "ready-to-work" state.
 * This class allows to configure different processes that the IDE needs in order to successfully and completely open the required projects.
 * Typical examples of the clients of this API are build systems (Maven, Gradle) and SDK detectors (Python SDK, JDK).
 *
 * Obsolescence notice: A more complete approach would be to use [com.intellij.ide.observation.ActivityInProgressPredicate]
 */
@ApiStatus.Obsolete
interface WarmupConfigurator {

  companion object {
    val EP_NAME: ExtensionPointName<WarmupConfigurator> = ExtensionPointName("com.intellij.warmupConfigurator")
  }

  /**
   * A name of the configuration.
   * The configurations can be disabled by the user, so they must be referable by some name.
   */
  val configuratorPresentableName: @NlsSafe String

  /**
   * Called **before** opening the project.
   *
   * This method helps to configure the behavior of some startup activities,
   * i.e. specifying the needed system properties.
   *
   * @param projectPath a path to the directory passed to warm-up process.
   */
  suspend fun prepareEnvironment(projectPath: Path) {}

  /**
   * Called **after** opening the project.
   *
   * This method actually performs the setting up of the project model.
   *
   * @return `true` if some globally visible changes to the project have been performed
   * or `false` otherwise
   */
  suspend fun runWarmup(project: Project): Boolean
}