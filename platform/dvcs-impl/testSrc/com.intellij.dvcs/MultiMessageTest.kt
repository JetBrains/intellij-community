// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.VcsPlatformTestContext
import com.intellij.vcs.test.projectPathFixture
import com.intellij.vcs.test.vcsPlatformContextFixture
import com.intellij.vcs.test.vcsTestRootFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class MultiMessageTest {
  private val fixture = vcsPlatformContextFixture(vcsTestRootFixture().projectPathFixture("idea"))
  private val context: VcsPlatformTestContext get() = fixture.get()

  @Test
  fun `test empty message`(): Unit = with(context) {
    val multiRootMessage = multiRootMessage(projectRoot)
    assertThat(multiRootMessage.asString()).isEmpty()
  }

  @Test
  fun `test single repository`(): Unit = with(context) {
    val multiRootMessage = multiRootMessage(projectRoot)
    multiRootMessage.append(projectRoot, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertThat(multiRootMessage.asString()).isEqualTo("Pruned obsolete remote references: origin/fix1, origin/fix2")
  }

  @Test
  fun `test single root message in multi-root project`(): Unit = with(context) {
    val idea = projectRoot
    val community = createSubDir(idea, "community")

    val multiRootMessage = multiRootMessage(idea, community)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertThat(multiRootMessage.asString()).isEqualTo("Pruned obsolete remote references: origin/fix1, origin/fix2 in idea")
  }

  @Test
  fun `test two roots with same messages`(): Unit = with(context) {
    val idea = projectRoot
    val community = createSubDir(idea, "community")

    val multiRootMessage = multiRootMessage(idea, community)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    assertThat(multiRootMessage.asString()).isEqualTo("Pruned obsolete remote references: origin/fix1, origin/fix2")
  }

  @Test
  fun `test two roots with different messages`(): Unit = with(context) {
    val idea = projectRoot
    val community = createSubDir(idea, "community")

    val multiRootMessage = multiRootMessage(idea, community)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix3")
    assertThat(multiRootMessage.asString()).isEqualTo(
      """
        Pruned obsolete remote references: origin/fix1, origin/fix2 in idea
        Pruned obsolete remote references: origin/fix3 in community
        """.trimIndent())
  }

  @Test
  fun `test two roots with different messages and prefix notation`(): Unit = with(context) {
    val idea = projectRoot
    val community = createSubDir(idea, "community")

    val multiRootMessage = MultiRootMessage(project, listOf(idea, community), true, false)
    multiRootMessage.append(idea, "Could not read from remote repository.")
    multiRootMessage.append(community, "Authentication failed for 'https://login@bitbucket.org/login/repo.git/'")
    assertThat(multiRootMessage.asString()).isEqualTo(
      """
        idea: Could not read from remote repository.
        community: Authentication failed for 'https://login@bitbucket.org/login/repo.git/'
        """.trimIndent())
  }

  @Test
  fun `test html message for three roots with same message, and one root with another`(): Unit = with(context) {
    val idea = projectRoot
    val community = createSubDir(idea, "community")
    val contrib = createSubDir(idea, "contrib")
    val android = createSubDir(community, "android")

    val multiRootMessage = MultiRootMessage(project, setOf(idea, community, contrib, android), false, true)
    multiRootMessage.append(idea, "Pruned obsolete remote references: origin/fix1, origin/fix2")
    multiRootMessage.append(community, "Pruned obsolete remote references: origin/fix3")
    multiRootMessage.append(contrib, "Pruned obsolete remote references: origin/fix3")
    multiRootMessage.append(android, "Pruned obsolete remote references: origin/fix3")
    assertThat(multiRootMessage.asString()).isEqualTo(
      """
        Pruned obsolete remote references: origin/fix1, origin/fix2 in idea<br/>
        Pruned obsolete remote references: origin/fix3 in community, contrib and community${File.separator}android
        """.trimIndent())
  }

  private fun VcsPlatformTestContext.multiRootMessage(vararg roots: VirtualFile) = MultiRootMessage(project, roots.asList(), false, false)

  private fun createSubDir(parent: VirtualFile, name: String): VirtualFile {
    return runWriteAction {
      parent.createChildDirectory(this, name)
    }
  }
}
