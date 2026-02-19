// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.impl.jar.JarFileSystemImpl
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.generateInVirtualTempDir
import com.intellij.util.io.zipFile

class ConvertToRepositoryLibraryActionTest : LightPlatformTestCase() {
  fun `test detect single coordinates`() {
    val file = zipFile {
      dir("META-INF") {
        dir("maven") {
          dir("myGroupId") {
            dir("myArtifactId") {
              file("pom.properties", """
                  |version=1.0
                  |groupId=myGroupId
                  |artifactId=myArtifactId
                """.trimMargin())
            }
          }
        }
      }
    }.generateInVirtualTempDir()
    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file)!!
    val coordinates = assertOneElement(ConvertToRepositoryLibraryActionBase.detectMavenCoordinates(arrayOf(jarRoot)))
    assertEquals("myGroupId", coordinates.groupId)
    assertEquals("myArtifactId", coordinates.artifactId)
    assertEquals("1.0", coordinates.version)
  }

  fun `test detect multiple coordinates`() {
    val file = zipFile {
      dir("META-INF") {
        dir("maven") {
          dir("myGroupId") {
            dir("myArtifactId1") {
              file("pom.properties", """
                  |version=1.0
                  |groupId=myGroupId
                  |artifactId=myArtifactId1
                """.trimMargin())
            }
            dir("myArtifactId2") {
              file("pom.properties", """
                  |version=1.0
                  |groupId=myGroupId
                  |artifactId=myArtifactId2
                """.trimMargin())
            }
          }
        }
      }
    }.generateInVirtualTempDir()
    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file)!!
    val (coordinates1, coordinates2) = ConvertToRepositoryLibraryActionBase.detectMavenCoordinates(arrayOf(jarRoot)).sortedBy { it.artifactId }
    assertEquals("myGroupId", coordinates1.groupId)
    assertEquals("myArtifactId1", coordinates1.artifactId)
    assertEquals("1.0", coordinates1.version)
    assertEquals("myGroupId", coordinates2.groupId)
    assertEquals("myArtifactId2", coordinates2.artifactId)
    assertEquals("1.0", coordinates2.version)
  }

  override fun tearDown() {
    try {
      JarFileSystemImpl.cleanupForNextTest()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}