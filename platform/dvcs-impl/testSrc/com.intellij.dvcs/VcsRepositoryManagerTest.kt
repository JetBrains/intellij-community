// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryCreator
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.vcs.test.VcsPlatformTestContext
import com.intellij.vcs.test.vcsPlatformContextFixture
import com.intellij.vcsUtil.VcsUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

private const val LOCK_ERROR_TEXT = "Possible dead lock occurred!"
private const val TIMEOUT_SECONDS = 10L

@TestApplication
class VcsRepositoryManagerTest {
  private val fixture = vcsPlatformContextFixture()
  private val context: VcsPlatformTestContext get() = fixture.get()

  private lateinit var vcs: MockAbstractVcs
  private lateinit var continueModify: CountDownLatch
  private lateinit var readyToRead: CountDownLatch

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable): Unit = with(context) {
    cd(projectRoot)

    vcs = MockAbstractVcs(project)
    vcsManager.registerVcs(vcs)
    readyToRead = CountDownLatch(1)
    continueModify = CountDownLatch(1)

    val mockCreator = createMockRepositoryCreator()
    ExtensionTestUtil.maskExtensions(VcsRepositoryManager.EP_NAME, listOf(mockCreator), disposable, false)
  }

  @Test
  fun testRepositoryInfoReadingWhileModifying(): Unit = with(context) {
    val repositoryFile = createExternalRepository()
    val repositoryManager = VcsRepositoryManager.getInstance(project)
    assertThat(repositoryManager.getRepositoryForRoot(repositoryFile)).isNotNull()

    val readExistingRepo = FutureTask { repositoryManager.getRepositoryForRoot(repositoryFile) }

    val modifyRepositoryMapping = FutureTask {
      vcsManager.setDirectoryMappings(VcsUtil.addMapping(vcsManager.getDirectoryMappings(), projectRoot.path, vcs.name))
      repositoryManager.waitForAsyncTaskCompletion()
      repositoryManager.getRepositories().isNotEmpty()
    }
    val modify = ApplicationManager.getApplication().executeOnPooledThread(modifyRepositoryMapping)

    //wait until modification starts
    assertThat(readyToRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).withFailMessage(LOCK_ERROR_TEXT).isTrue()

    val read = ApplicationManager.getApplication().executeOnPooledThread(readExistingRepo)
    assertThat(readExistingRepo.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull()
    continueModify.countDown()
    assertThat(modifyRepositoryMapping.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()
    read.get()
    modify.get()
  }

  private fun VcsPlatformTestContext.createExternalRepository(): VirtualFile {
    cd(projectRoot)
    val externalName = "external"
    mkdir(externalName)
    projectRoot.refresh(false, true)
    val repositoryFile = requireNotNull(projectRoot.findChild(externalName))
    val externalRepo = MockRepositoryImpl(project, repositoryFile, project)
    VcsRepositoryManager.getInstance(project).addExternalRepository(repositoryFile, externalRepo)
    return repositoryFile
  }

  private fun createMockRepositoryCreator(): VcsRepositoryCreator {
    return object : VcsRepositoryCreator {
      override fun getVcsKey(): VcsKey = vcs.keyInstanceMethod

      override fun createRepositoryIfValid(project: Project, root: VirtualFile, parentDisposable: Disposable): Repository? {
        readyToRead.countDown()
        try {
          //wait until reading thread gets existing info
          if (!continueModify.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null
        }
        catch (_: InterruptedException) {
          return null
        }
        return MockRepositoryImpl(project, root, parentDisposable)
      }
    }
  }
}
