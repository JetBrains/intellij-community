// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.util.application
import org.junit.Assert
import java.io.File
import java.io.InputStream
import java.io.OutputStream

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

  fun createMockVirtualFile(
    name: String = "mock file",
    path: String = "/mock file",
    writable: Boolean = false,
    directory: Boolean = false,
    valid: Boolean = true,
    length: Long = 0,
    timestamp: Long = 0,
    modificationStamp: Long = 0,
  ): VirtualFile {
    return object : VirtualFile() {
      override fun getName(): String = name
      override fun getPath(): String = path
      override fun isWritable(): Boolean = writable
      override fun isDirectory(): Boolean = directory
      override fun isValid(): Boolean = valid
      override fun getChildren(): Array<VirtualFile> = emptyArray()
      override fun getLength(): Long = length
      override fun getTimeStamp(): Long = timestamp
      override fun getModificationStamp(): Long = modificationStamp

      override fun getFileSystem(): VirtualFileSystem = TODO("Not yet implemented")

      override fun getParent(): VirtualFile = TODO("Not yet implemented")

      override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        TODO("Not yet implemented")
      }

      override fun contentsToByteArray(): ByteArray {
        TODO("Not yet implemented")
      }

      override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        TODO("Not yet implemented")
      }

      override fun getInputStream(): InputStream = TODO("Not yet implemented")

      override fun toString(): String {
        return "TestFactories#createMockVirtualFile@" + hashCode()
      }
    }
  }
}