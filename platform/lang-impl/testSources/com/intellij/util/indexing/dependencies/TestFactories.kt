// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.util.application
import org.junit.Assert
import java.io.File

class TestFactories(private val tmpDir: File,
                    private val testDisposable: Disposable,
                    private val useApplication: Boolean) {

  val sharedAppService: AppIndexingDependenciesService by lazy {
    if (useApplication) {
      application.service<AppIndexingDependenciesService>()
    }
    else {
      newAppIndexingDependenciesService(nonExistingFile("appStorage"))
    }
  }

  fun newProjectIndexingDependenciesService(): ProjectIndexingDependenciesService {
    return newProjectIndexingDependenciesService(nonExistingFile(), sharedAppService)
  }

  fun newProjectIndexingDependenciesService(appService: AppIndexingDependenciesService): ProjectIndexingDependenciesService {
    return newProjectIndexingDependenciesService(nonExistingFile(), appService)
  }

  fun newProjectIndexingDependenciesService(file: File): ProjectIndexingDependenciesService {
    return newProjectIndexingDependenciesService(file, sharedAppService)
  }

  fun newProjectIndexingDependenciesService(file: File,
                                            appService: AppIndexingDependenciesService): ProjectIndexingDependenciesService {
    return ProjectIndexingDependenciesService(file.toPath(), appService).also {
      Disposer.register(testDisposable, it)
      Assert.assertTrue(file.exists())
    }
  }

  fun newAppIndexingDependenciesService(): AppIndexingDependenciesService {
    return newAppIndexingDependenciesService(nonExistingFile())
  }

  fun newAppIndexingDependenciesService(file: File): AppIndexingDependenciesService {
    return AppIndexingDependenciesService(file.toPath()).also {
      Disposer.register(testDisposable, it)
      Assert.assertTrue(file.exists())
    }
  }

  fun nonExistingFile(s: String = "storage"): File {
    val file = tmpDir.resolve(s)
    Assert.assertFalse(file.exists())
    return file
  }
}