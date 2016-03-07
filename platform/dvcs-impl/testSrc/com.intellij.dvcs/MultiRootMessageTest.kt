/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.dvcs

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class MultiRootMessageTest {

  private lateinit var myProject : Project

  @Before
  fun setUp() {
    myProject = mock(Project::class.java)
    Mockito.`when`(myProject.baseDir).thenReturn(MockVirtualFile(true, "idea"))
  }

  @Test
  fun `test empty message`() {
    val root = myProject.baseDir
    val multiRootMessage = MultiRootMessage(myProject, setOf(root))
    assertTrue(multiRootMessage.asString().isEmpty())
  }

  @Test
  fun `test single repository`() {
    val root = myProject.baseDir
    val multiRootMessage = MultiRootMessage(myProject, setOf(root))
    multiRootMessage.append(root, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertEquals("Pruned obsolete remote references: origin/fix1, origin/fix2", multiRootMessage.asString())
  }

  @Test
  fun `test single root message in multi-root project`() {
    val idea = myProject.baseDir
    val community = createSubDir(idea, "community")

    val multiRootMessage = MultiRootMessage(myProject, setOf(idea, community))
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertEquals("Pruned obsolete remote references: origin/fix1, origin/fix2 in idea", multiRootMessage.asString())
  }

  @Test
  fun `test two roots with same messages`() {
    val idea = myProject.baseDir
    val community = createSubDir(idea, "community")

    val multiRootMessage = MultiRootMessage(myProject, setOf(idea, community))
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertEquals("Pruned obsolete remote references: origin/fix1, origin/fix2", multiRootMessage.asString())
  }

  @Test
  fun `test two roots with different messages`() {
    val idea = myProject.baseDir
    val community = createSubDir(idea, "community")

    val multiRootMessage = MultiRootMessage(myProject, setOf(idea, community))
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix3")
    assertEquals(
        """
        Pruned obsolete remote references: origin/fix1, origin/fix2 in idea
        Pruned obsolete remote references: origin/fix3 in community
        """.trimIndent(), multiRootMessage.asString())
  }

  @Test
  fun `test three roots with same message, and one root with another`() {
    val idea = myProject.baseDir
    val community = createSubDir(idea, "community")
    val contrib = createSubDir(idea, "contrib")
    val android = createSubDir(community, "android")

    val multiRootMessage = MultiRootMessage(myProject, setOf(idea, community, contrib, android))
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix3")
    multiRootMessage.append(contrib, "Pruned obsolete remote references: origin/fix3")
    multiRootMessage.append(android, "Pruned obsolete remote references: origin/fix3")
    assertEquals(
        """
        Pruned obsolete remote references: origin/fix1, origin/fix2 in idea
        Pruned obsolete remote references: origin/fix3 in community, contrib and community/android
        """.trimIndent(), multiRootMessage.asString())
  }

  private fun createSubDir(parent: VirtualFile, name: String): VirtualFile {
    val vf = MockVirtualFile(true, name)
    vf.parent = parent
    return vf
  }
}