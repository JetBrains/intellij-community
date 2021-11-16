// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.roots.NativeLibraryOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.impl.LibraryRootsDetectorImpl
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

class JavaLibraryRootsDetectionTest : LightPlatformTestCase() {
  fun `test JAR with classes`() {
    assertRootType(OrderRootType.CLASSES, false) {
      zip("classes.jar") {
        file("A.class")
      }
    }
  }

  fun `test directory with classes`() {
    assertRootType(OrderRootType.CLASSES, false) {
      dir("classes") {
        file("A.class")
      }
    }
  }

  fun `test directory with sources`() {
    assertRootType(OrderRootType.SOURCES, false) {
      dir("src") {
        file("A.java", "class A {}")
      }
    }
  }

  fun `test JAR directory`() {
    assertRootType(OrderRootType.CLASSES, true) {
      dir("lib") {
        zip("a.jar") {
          file("A.class")
        }
      }
    }
  }

  fun `test sources zip directory`() {
    assertRootType(OrderRootType.SOURCES, true) {
      dir("lib") {
        zip("src.zip") {
          file("A.java", "class A {}")
        }
      }
    }
  }

  fun `test native library`() {
    assertRootType(NativeLibraryOrderRootType.getInstance(), false) {
      dir("lib") {
        file("a.dll")
      }
    }
  }

  fun `test native library in JAR`() {
    assertRootType(OrderRootType.CLASSES, false) {
      zip("a.jar") {
        file("a.dll")
      }
    }
  }

  private fun assertRootType(expectedType: OrderRootType, jarDirectory: Boolean, content: DirectoryContentBuilder.() -> Unit) {
    val dir = directoryContent(content).generateInVirtualTempDir()
    val detector = LibraryRootsDetectorImpl(DefaultLibraryRootsComponentDescriptor().rootDetectors)
    val root = assertOneElement(dir.children.flatMap { file ->
      val rootFile = if (FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE)) JarFileSystem.getInstance().getJarRootForLocalFile(file)!! else file
      detector.detectRoots(rootFile, EmptyProgressIndicator())
    })
    val type = assertOneElement(root.types)
    assertEquals(expectedType, type.type)
    assertEquals(jarDirectory, type.isJarDirectory)
  }

  override fun tearDown() {
    JarFileSystemImpl.cleanupForNextTest()
    super.tearDown()
  }
}