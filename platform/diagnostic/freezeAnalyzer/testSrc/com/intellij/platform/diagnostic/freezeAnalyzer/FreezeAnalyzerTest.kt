// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.freezeAnalyzer

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException

class FreezeAnalyzerTest {
  private fun getResourceContent(path: String): String {
    val classLoader = this::class.java.classLoader
    val stream = classLoader.getResourceAsStream(path)
    if (stream == null) {
      throw FileNotFoundException("Not found '$path' in classloader '$classLoader' classpath")
    }
    return stream.reader().use { it.readText() }
  }

  @Test
  fun testBackgroundWrite() {
    val threadDump = getResourceContent("freezes/readWriteLock/backgroundWrite.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider.checkAccess")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.threads?.first()?.stackTrace?.lineSequence()?.first().shouldBe("\"DefaultDispatcher-worker-71\" prio=0 tid=0x0 nid=0x0 runnable")
  }

  @Test
  fun testDeadlockWithSuvorovIndicator() {
    val threadDump = getResourceContent("freezes/readWriteLock/deadLock.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.idea.maven.buildtool.MavenSyncConsole.doFinish")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.threads?.first()?.stackTrace?.lineSequence()?.first().shouldBe("\"java\" prio=0 tid=0x0 nid=0x0 blocked")
  }

  @Test
  fun testSuvorovIndicator() {
    val threadDump = getResourceContent("freezes/readWriteLock/suvorov-indicator.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.codeInsight.NullabilitySource\$MultiSource.hashCode")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.threads?.first()?.stackTrace?.lineSequence()?.first().shouldBe("\"JobScheduler FJ pool 11/19\" prio=0 tid=0x0 nid=0x0 runnable")
  }

  @Test
  fun testAWTFreeze1() {
    val threadDump = getResourceContent("freezes/awtFreeze/IDEA-344485.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.vcs.log.data.VcsLogUserResolverBase.resolveCurrentUser")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.threads?.first()?.stackTrace?.lineSequence()?.first().shouldBe("\"AWT-EventQueue-0\" prio=0 tid=0x0 nid=0x0 runnable")
  }

  @Test
  fun testAWTFreeze2() {
    val threadDump = getResourceContent("freezes/awtFreeze/IDEA-342509.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.ui.tree.ui.DefaultTreeUI.getRenderer")
  }

  @Test
  fun testAWTFreeze3() {
    val threadDump = getResourceContent("freezes/awtFreeze/KTIJ-28701.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with org.jetbrains.kotlin.idea.base.projectStructure.KotlinProjectStructureUtils\$hasKotlinJvmRuntime\$1.invoke")
  }

  @Test
  fun testAWTFreeze4() {
    val threadDump = getResourceContent("freezes/awtFreeze/IDEA-344941.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.notification.impl.NotificationsManagerImpl.calculateContentHeight")
  }

  @Test
  fun testAWTFreeze5() {
    val threadDump = getResourceContent("freezes/awtFreeze/IDEA-344178.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.configurationStore.ComponentSerializationUtil.getStateClass")
  }

  @Test
  fun testFreezeInExcluded() {
    val threadDump = getResourceContent("freezes/awtFreeze/FreezeInExcluded.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesStorage.writeIncompleteScanningMark")
  }

  @Test
  fun testLockFreeze1() {
    val threadDump = getResourceContent("freezes/readWriteLock/ML-2562.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.completion.full.line.local.generation.SimilarContextRetriever.getSimilarity")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.threads?.joinToString { it -> it.stackTrace }.shouldContain("\"DefaultDispatcher-worker-27\" prio=0 tid=0x0 nid=0x0 runnable")
  }

  @Test
  fun testLockFreeze2() {
    val threadDump = getResourceContent("freezes/readWriteLock/KTIJ-23464.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.kotlin.org.eclipse.aether.internal.impl.synccontext.named.DiscriminatingNameMapper.getHostname")
  }

  @Test
  fun testLockFreeze3() {
    val threadDump = getResourceContent("freezes/readWriteLock/IDEA-344522.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.jetbrains.jsonSchema.impl.PatternProperties.<init>")
  }

  @Test
  fun testLockFreeze4() {
    val threadDump = getResourceContent("freezes/readWriteLock/WEB-65320.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil.findChildPackageJsonFile")
  }

  @Test
  fun testLockFreeze5() {
    val threadDump = getResourceContent("freezes/readWriteLock/KT-65652.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPluginsKt.readFileContentFromJar")
  }

  @Test
  fun testGeneralLockFreeze() {
    val threadDump = getResourceContent("freezes/generalLock/generalLock.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is blocked on com.intellij.codeInsight.completion.CompletionProgressIndicator.blockingWaitForFinish")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.additionalMessage?.shouldBe("Possibly locked by com.intellij.codeInsight.completion.JavaMethodCallElement.<init> in DefaultDispatcher-worker-55")
  }

  @Test
  fun testGeneralLock2Freeze() {
    val threadDump = getResourceContent("freezes/generalLock/generalLock2.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is blocked on org.jetbrains.kotlin.idea.base.projectStructure.KotlinProjectStructureUtils\$hasKotlinJvmRuntime\$1.invoke")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.additionalMessage.shouldBe("Possibly locked by com.intellij.lang.javascript.psi.stubs.JSUsedRemoteModulesIndex.getUsedModules in ApplicationImpl pooled thread 10")
  }

  @Test
  fun testGeneralLockFreezeWithLockId() {
    val threadDump = getResourceContent("freezes/generalLock/generalLockWithLockId.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Possible deadlock. Read lock is taken by com.intellij.execution.impl.RunManagerImpl.getSelectedConfiguration, but the thread is blocked by com.android.tools.idea.gradle.project.GradleProjectInfo.isBuildWithGradle which is waiting on RWLock")
  }

  @Test
  fun testReadWriteLockInCoreClasses() {
    val threadDump = getResourceContent("freezes/readWriteLock/NullLock.txt")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.util.indexing.dependencies.AppIndexingDependenciesStorage.writeRequestId")
  }

  @Test
  fun testReadWriteNewLock() {
    val threadDump = getResourceContent("freezes/readWriteLock/NewLock.txt")
    withClue("") {
      assertSoftly {
        FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.openapi.roots.impl.PackageDirectoryCacheImpl\$PackageInfo.lambda\$new\$0")
        FreezeAnalyzer.analyzeFreeze(threadDump)?.threads?.joinToString { it -> it.stackTrace }.shouldStartWith("\"DefaultDispatcher-worker-22\" prio=0 tid=0x0 nid=0x0 waiting on condition")
      }
    }
  }

  @Test
  fun testNoFreeze() {
    val threadDump = getResourceContent("freezes/noFreeze/lowMemory.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump, testName = "kotlin/testCase")?.message.shouldBe("kotlin/testCase: EDT is not blocked/busy (freeze can be the result of extensive GC)")
  }

  @Test
  fun testNoFreeze2() {
    val threadDump = getResourceContent("freezes/noFreeze/lowMemory2.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump, testName = "kotlin/testCase").shouldBe(null)
  }

  @Test
  fun testNoFreezeOutsideOfTest() {
    val threadDump = getResourceContent("freezes/noFreeze/lowMemory.txt").replace("2.", ".")
    FreezeAnalyzer.analyzeFreeze(threadDump, testName = null)?.shouldBe(null)
  }

  @Test
  fun testCoroutines() {
    val threadDump = getResourceContent("freezes/awtFreeze/coroutines.txt").replace("2.", ".")
    val analysis = FreezeAnalyzer.analyzeFreeze(threadDump, testName = null)
    analysis?.message.shouldBe("EDT is blocked on com.intellij.lang.javascript.service.JSLanguageServiceBase._get_process_\$lambda\$2 which called runBlocking")
    analysis?.threads?.joinToString { it -> it.stackTrace }.shouldStartWith("\"AWT-EventQueue-0\" prio=0 tid=0x0 nid=0x0 waiting on condition")
  }
}