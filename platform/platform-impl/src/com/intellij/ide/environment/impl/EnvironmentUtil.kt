// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

// reduce discoverability
object EnvironmentUtil {
  private val pathToEnvironmentConfig : Key<Path> = Key.create("environment.configuration.file")

  fun getPathToConfigurationFile() : Path? {
    return ApplicationManager.getApplication().getUserData(pathToEnvironmentConfig)
  }

  @Internal
  fun setPathToConfigurationFile(path: Path) {
    val application = ApplicationManager.getApplication()
    val currentPath = application.getUserData(pathToEnvironmentConfig)
    if (currentPath != null && !application.isUnitTestMode) {
      thisLogger().error("The path to environment config can be set only once")
    }
    if (!path.isAbsolute) {
      thisLogger().error("The path to environment config must be absolute")
    }
    application.putUserData(pathToEnvironmentConfig, path)
  }

  @TestOnly
  fun setPathTemporarily(path: Path, disposable: Disposable) {
    val application = ApplicationManager.getApplication()
    assert(application.isUnitTestMode)
    setPathToConfigurationFile(path)

    Disposer.register(disposable) {
      application.putUserData(pathToEnvironmentConfig, null)
    }
  }

}