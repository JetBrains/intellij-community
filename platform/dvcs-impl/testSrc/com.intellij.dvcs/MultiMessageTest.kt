// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Test
import java.io.File
import java.nio.file.Path

class MultiMessageTest : HeavyPlatformTestCase() {
  override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
    assertFalse(isDirectoryBasedProject)
    val testFolder = FileUtil.sanitizeFileName(name, false)
    return tempDir.newPath("$testFolder/idea/project${ProjectFileType.DOT_DEFAULT_EXTENSION}")
  }

  @Test
  fun `test empty message`() {
    val root = getOrCreateProjectBaseDir()
    val multiRootMessage = multiRootMessage(root)
    assertTrue(multiRootMessage.asString().isEmpty())
  }

  @Test
  fun `test single repository`() {
    val root = getOrCreateProjectBaseDir()
    val multiRootMessage = multiRootMessage(root)
    multiRootMessage.append(root, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertEquals("Pruned obsolete remote references: origin/fix1, origin/fix2", multiRootMessage.asString())
  }

  @Test
  fun `test single root message in multi-root project`() {
    val idea = getOrCreateProjectBaseDir()
    val community = createSubDir(idea, "community")

    val multiRootMessage = multiRootMessage(idea, community)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertEquals("Pruned obsolete remote references: origin/fix1, origin/fix2 in idea", multiRootMessage.asString())
  }

  @Test
  fun `test two roots with same messages`() {
    val idea = getOrCreateProjectBaseDir()
    val community = createSubDir(idea, "community")

    val multiRootMessage = multiRootMessage(idea, community)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertEquals("Pruned obsolete remote references: origin/fix1, origin/fix2", multiRootMessage.asString())
  }

  @Test
  fun `test two roots with different messages`() {
    val idea = getOrCreateProjectBaseDir()
    val community = createSubDir(idea, "community")

    val multiRootMessage = multiRootMessage(idea, community)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix3")
    assertEquals(
      """
        Pruned obsolete remote references: origin/fix1, origin/fix2 in idea
        Pruned obsolete remote references: origin/fix3 in community
        """.trimIndent(), multiRootMessage.asString())
  }

  @Test
  fun `test two roots with different messages and prefix notation`() {
    val idea = getOrCreateProjectBaseDir()
    val community = createSubDir(idea, "community")

    val multiRootMessage = MultiRootMessage(project, listOf(idea, community), true, false)
    multiRootMessage.append(idea, "Could not read from remote repository.")
    multiRootMessage.append(community, "Authentication failed for 'https://login@bitbucket.org/login/repo.git/'")
    assertEquals(
      """
        idea: Could not read from remote repository.
        community: Authentication failed for 'https://login@bitbucket.org/login/repo.git/'
        """.trimIndent(), multiRootMessage.asString())
  }

  @Test
  fun `test html message for three roots with same message, and one root with another`() {
    val idea = getOrCreateProjectBaseDir()
    val community = createSubDir(idea, "community")
    val contrib = createSubDir(idea, "contrib")
    val android = createSubDir(community, "android")

    val multiRootMessage = MultiRootMessage(project, setOf(idea, community, contrib, android), false, true)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix3")
    multiRootMessage.append(contrib, "Pruned obsolete remote references: origin/fix3")
    multiRootMessage.append(android, "Pruned obsolete remote references: origin/fix3")
    assertEquals(
      """
        Pruned obsolete remote references: origin/fix1, origin/fix2 in idea<br/>
        Pruned obsolete remote references: origin/fix3 in community, contrib and community${File.separator}android
        """.trimIndent(), multiRootMessage.asString())
  }

  private fun multiRootMessage(vararg roots: VirtualFile) = MultiRootMessage(project, roots.asList(), false, false)

  private fun createSubDir(parent: VirtualFile, name: String): VirtualFile {
    return runWriteAction {
      parent.createChildDirectory(this, name)
    }
  }
}