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

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightPlatformTestCase

/**
 * @author nik
 */
class ConvertToRepositoryLibraryActionTest : LightPlatformTestCase() {
  fun `test read Maven coordinates`() {
    val jarFile = PathManager.findFileInLibDirectory("commons-codec-1.9.jar")
    val file = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(jarFile))
    assertNotNull(jarFile.absolutePath, file)
    val coordinates = assertOneElement(ConvertToRepositoryLibraryActionBase.detectMavenCoordinates(arrayOf(file!!)))
    assertEquals("commons-codec", coordinates.groupId)
    assertEquals("commons-codec", coordinates.artifactId)
    assertEquals("1.9", coordinates.version)
  }
}