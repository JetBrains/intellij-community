/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.libraryEditor

import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.roots.NativeLibraryOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.ui.impl.LibraryRootsDetectorImpl
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

/**
 * @author nik
 */
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
      val rootFile = if (file.fileType == StdFileTypes.ARCHIVE) JarFileSystem.getInstance().getJarRootForLocalFile(file)!! else file
      detector.detectRoots(rootFile, EmptyProgressIndicator())
    })
    val type = assertOneElement(root.types)
    assertEquals(expectedType, type.type)
    assertEquals(jarDirectory, type.isJarDirectory)
  }
}