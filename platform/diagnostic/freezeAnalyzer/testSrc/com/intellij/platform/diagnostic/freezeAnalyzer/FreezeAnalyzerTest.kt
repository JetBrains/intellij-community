package com.intellij.platform.diagnostic.freezeAnalyzer

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.readText

class FreezeAnalyzerTest {
  @Test
  fun testAWTFreeze1() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/awtFreeze/IDEA-344485.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.vcs.log.data.VcsLogUserResolverBase.resolveCurrentUser")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.stackTrace?.lineSequence()?.first().shouldBe("\"AWT-EventQueue-0\" prio=0 tid=0x0 nid=0x0 runnable")
  }

  @Test
  fun testAWTFreeze2() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/awtFreeze/IDEA-342509.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.ui.tree.ui.DefaultTreeUI.getRenderer")
  }

  @Test
  fun testAWTFreeze3() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/awtFreeze/KTIJ-28701.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with org.jetbrains.kotlin.idea.base.projectStructure.KotlinProjectStructureUtils\$hasKotlinJvmRuntime\$1.invoke")
  }

  @Test
  fun testAWTFreeze4() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/awtFreeze/IDEA-344941.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.notification.impl.NotificationsManagerImpl.calculateContentHeight")
  }

  @Test
  fun testAWTFreeze5() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/awtFreeze/IDEA-344178.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.configurationStore.ComponentSerializationUtil.getStateClass")
  }

  @Test
  fun testFreezeInExcluded() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/awtFreeze/FreezeInExcluded.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is busy with com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesStorage.writeIncompleteScanningMark")
  }

  @Test
  fun testLockFreeze1() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/ML-2562.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.completion.full.line.local.generation.SimilarContextRetriever.getSimilarity")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.stackTrace?.shouldContain("\"DefaultDispatcher-worker-27\" prio=0 tid=0x0 nid=0x0 runnable")
  }

  @Test
  fun testLockFreeze2() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/KTIJ-23464.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.kotlin.org.eclipse.aether.internal.impl.synccontext.named.DiscriminatingNameMapper.getHostname")
  }

  @Test
  fun testLockFreeze3() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/IDEA-344522.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.jetbrains.jsonSchema.impl.PatternProperties.<init>")
  }

  @Test
  fun testLockFreeze4() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/WEB-65320.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.lang.javascript.buildTools.npm.PackageJsonUtil.findChildPackageJsonFile")
  }

  @Test
  fun testLockFreeze5() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/KT-65652.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in org.jetbrains.kotlin.idea.fir.extensions.KotlinK2BundledCompilerPluginsKt.readFileContentFromJar")
  }

  @Test
  fun testGeneralLockFreeze() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/generalLock/generalLock.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is blocked on com.intellij.codeInsight.completion.CompletionProgressIndicator.blockingWaitForFinish")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.stackTrace?.shouldStartWith("Possibly locked by com.intellij.codeInsight.completion.JavaMethodCallElement.<init> in DefaultDispatcher-worker-55")
  }

  @Test
  fun testGeneralLock2Freeze() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/generalLock/generalLock2.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("EDT is blocked on org.jetbrains.kotlin.idea.base.projectStructure.KotlinProjectStructureUtils\$hasKotlinJvmRuntime\$1.invoke")
    FreezeAnalyzer.analyzeFreeze(threadDump)?.stackTrace?.shouldStartWith("Possibly locked by com.intellij.lang.javascript.psi.stubs.JSUsedRemoteModulesIndex.getUsedModules in ApplicationImpl pooled thread 10")
  }

  @Test
  fun testGeneralLockFreezeWithLockId() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/generalLock/generalLockWithLockId.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Possible deadlock. Read lock is taken by com.intellij.execution.impl.RunManagerImpl.getSelectedConfiguration, but the thread is blocked by com.android.tools.idea.gradle.project.GradleProjectInfo.isBuildWithGradle which is waiting on RWLock")
  }

  @Test
  fun testReadWriteLockInCoreClasses() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/NullLock.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.util.indexing.dependencies.AppIndexingDependenciesStorage.writeRequestId")
  }

  @Test
  fun testReadWriteNewLock() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/readWriteLock/NewLock.txt")!!.path).toPath().readText()
    withClue("") {
      assertSoftly {
        FreezeAnalyzer.analyzeFreeze(threadDump)?.message.shouldBe("Long read action in com.intellij.openapi.roots.impl.PackageDirectoryCacheImpl\$PackageInfo.lambda\$new\$0")
        FreezeAnalyzer.analyzeFreeze(threadDump)?.stackTrace.shouldStartWith("\"DefaultDispatcher-worker-22\" prio=0 tid=0x0 nid=0x0 waiting on condition")
      }
    }
  }

  @Test
  fun testNoFreeze() {
    val threadDump = File(this::class.java.classLoader.getResource("freezes/noFreeze/lowMemory.txt")!!.path).toPath().readText()
    FreezeAnalyzer.analyzeFreeze(threadDump, testName = "kotlin/testCase")?.message.shouldBe("kotlin/testCase: EDT is not blocked/busy (freeze can be the result of extensive GC)")
  }
}