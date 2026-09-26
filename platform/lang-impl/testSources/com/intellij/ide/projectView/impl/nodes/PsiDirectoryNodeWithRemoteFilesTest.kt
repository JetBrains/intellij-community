// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.ide.projectView.ViewSettings
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.platform.testFramework.junit5.eel.fixture.tempDirFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.io.createDirectories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests the trade-off in [PsiDirectoryNode.isAlwaysShowPlus]: ideally the project view would always show the exact expand marker state,
 * but computing it for remote directories may require loading children from a remote file system and become too expensive.
 */
@TestApplication
internal class PsiDirectoryNodeWithRemoteFilesTest {
  @Suppress("DEPRECATION")
  private val eelFixture = eelFixture(EelPlatform.Linux(EelPlatform.Arch.Unknown))
  @Suppress("DEPRECATION")
  private val remoteDirectoryFixture = eelFixture.tempDirFixture()
  private val localDirectoryFixture = tempPathFixture()
  private val projectFixture = projectFixture(openAfterCreation = true)

  @Test
  @RegistryKey(key = "ide.project.view.skip.loading.children.for.remote.directories", value = "true")
  fun `remote directory always shows plus without loading children`(): Unit = timeoutRunBlocking {
    val (node, directoryFile) = createRemoteDirectoryNode()

    assertThat(node.isAlwaysShowPlus).isTrue()
    assertThat(directoryFile.children).isEmpty()
  }

  @Test
  @RegistryKey(key = "ide.project.view.skip.loading.children.for.remote.directories", value = "false")
  fun `remote empty directory does not always show plus when skipping children loading is disabled`(): Unit = timeoutRunBlocking {
    val (node, directoryFile) = createRemoteDirectoryNode()

    assertThat(node.isAlwaysShowPlus).isFalse()
    assertThat(directoryFile.children).isEmpty()
  }

  @Test
  @RegistryKey(key = "ide.project.view.skip.loading.children.for.remote.directories", value = "true")
  fun `local empty directory does not always show plus when skipping children loading is enabled`(): Unit = timeoutRunBlocking {
    val (node, directoryFile) = createLocalDirectoryNode("emptyLocalDirectoryWithRegistryEnabled")

    assertThat(node.isAlwaysShowPlus).isFalse()
    assertThat(directoryFile.children).isEmpty()
  }

  @Test
  @RegistryKey(key = "ide.project.view.skip.loading.children.for.remote.directories", value = "false")
  fun `local empty directory does not always show plus when skipping children loading is disabled`(): Unit = timeoutRunBlocking {
    val (node, directoryFile) = createLocalDirectoryNode("emptyLocalDirectoryWithRegistryDisabled")

    assertThat(node.isAlwaysShowPlus).isFalse()
    assertThat(directoryFile.children).isEmpty()
  }

  private suspend fun createRemoteDirectoryNode(): Pair<PsiDirectoryNode, VirtualFile> {
    val emptyDirectoryPath = remoteDirectoryFixture.get()
    emptyDirectoryPath.createDirectories()

    val directoryFile = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(emptyDirectoryPath))
    assertThat(emptyDirectoryPath.getEelDescriptor()).isNotEqualTo(LocalEelDescriptor)

    return createDirectoryNode(directoryFile)
  }

  private suspend fun createLocalDirectoryNode(directoryName: String): Pair<PsiDirectoryNode, VirtualFile> {
    val emptyDirectoryPath = localDirectoryFixture.get().resolve(directoryName)
    emptyDirectoryPath.createDirectories()

    val directoryFile = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(emptyDirectoryPath))
    assertThat(emptyDirectoryPath.getEelDescriptor()).isEqualTo(LocalEelDescriptor)

    return createDirectoryNode(directoryFile)
  }

  private suspend fun createDirectoryNode(directoryFile: VirtualFile): Pair<PsiDirectoryNode, VirtualFile> {
    val project = projectFixture.get()
    val node = readAction {
      val psiDirectory = PsiManager.getInstance(project).findDirectory(directoryFile)
      PsiDirectoryNode(project, requireNotNull(psiDirectory), ViewSettings.DEFAULT)
    }

    return node to directoryFile
  }
}
