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
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

/**
 * @author nik
 */
class ConvertToRepositoryLibraryActionTest : LightPlatformTestCase() {
  fun `test detect single coordinates`() {
    val file = directoryContent {
      zip("library.jar") {
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
      }
    }.generateInVirtualTempDir().findChild("library.jar")!!
    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file)!!
    val coordinates = assertOneElement(ConvertToRepositoryLibraryActionBase.detectMavenCoordinates(arrayOf(jarRoot)))
    assertEquals("myGroupId", coordinates.groupId)
    assertEquals("myArtifactId", coordinates.artifactId)
    assertEquals("1.0", coordinates.version)
  }

  fun `test detect multiple coordinates`() {
    val file = directoryContent {
      zip("library2.jar") {
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
      }
    }.generateInVirtualTempDir().findChild("library2.jar")!!
    val jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file)!!
    val (coordinates1, coordinates2) = ConvertToRepositoryLibraryActionBase.detectMavenCoordinates(arrayOf(jarRoot)).sortedBy { it.artifactId }
    assertEquals("myGroupId", coordinates1.groupId)
    assertEquals("myArtifactId1", coordinates1.artifactId)
    assertEquals("1.0", coordinates1.version)
    assertEquals("myGroupId", coordinates2.groupId)
    assertEquals("myArtifactId2", coordinates2.artifactId)
    assertEquals("1.0", coordinates2.version)
  }
}