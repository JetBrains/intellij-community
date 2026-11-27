// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightFixture
import com.intellij.codeInsight.daemon.impl.UsageCounterConfiguration
import com.intellij.codeInsight.daemon.impl.UsagesCountManager
import com.intellij.concurrency.ConcurrencyUtils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class JavaCodeVisionCacheTest {
  private val fixture by heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeVision/")

  @Test
  fun testChangedMethodName(): Unit = timeoutRunBlocking {
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("random")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 0)
  }

  @Test
  fun testChangedClassName(): Unit = timeoutRunBlocking {
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("random")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 2)
  }

  @Test
  fun testAddedExternalUsage(): Unit = timeoutRunBlocking {
    configureAndOpenExternalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("Test.test();")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 4)
  }

  @Test
  fun testAddedLocalUsage(): Unit = timeoutRunBlocking {
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("test();")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 4)
  }

  @Test
  fun testUsagesNotRecomputed(): Unit = timeoutRunBlocking {
    val externalComputations = AtomicInteger(0)
    val localComputations = AtomicInteger(0)
    val testUsagesCounter = object : UsageCounterConfiguration {
      override fun countUsages(file: PsiFile, members: List<PsiMember>, scope: SearchScope): Int {
        if (scope.contains(file.virtualFile)) {
          localComputations.incrementAndGet()
        }
        else {
          externalComputations.incrementAndGet()
        }
        return 0
      }

      override fun <K : Any, V : Any> createCacheMap(): ConcurrentMap<K, V> {
        //cache with strong key-values to exclude GC impact
        return ConcurrentHashMap()
      }
    }
    val usagesCountManager = UsagesCountManager(fixture.project, testUsagesCounter)
    Disposer.register(fixture.testRootDisposable, usagesCountManager)
    require(localComputations.get() == 0)
    require(externalComputations.get() == 0)
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    countUsages(testMember, usagesCountManager)
    require(localComputations.get() == 1)
    require(externalComputations.get() == 1)
    typeAndCommit("test();")
    countUsages(testMember, usagesCountManager)
    require(localComputations.get() == 2)
    require(externalComputations.get() == 1)
  }

  private fun configureAndOpenLocalFile() {
    fixture.configureByFiles(null, "${getTestDirectory()}/Test.java", "${getTestDirectory()}/ExternalUsage.java")
  }

  private fun configureAndOpenExternalFile() {
    fixture.configureByFiles(null, "${getTestDirectory()}/ExternalUsage.java", "${getTestDirectory()}/Test.java")
  }

  private fun getTestDirectory(): String = fixture.getTestName(true)

  private suspend fun findTestMember(): PsiMember = readAction {
    val project = fixture.project
    val testClass = JavaPsiFacade.getInstance(project).findClass("Test", GlobalSearchScope.allScope(project))
    val externalClass = JavaPsiFacade.getInstance(project).findClass("ExternalUsage", GlobalSearchScope.allScope(project))
    require(externalClass != null)
    val member: PsiMember? = testClass?.findMethodsByName("test", false)?.firstOrNull()
    require(member != null)
    member
  }

  private suspend fun countUsages(member: PsiMember, manager: UsagesCountManager = UsagesCountManager.getInstance(fixture.project)): Int {
    return readActionBlocking {
      ConcurrencyUtils.runWithIndicatorOrContextCancellation {
        manager.countMemberUsages(member.containingFile, member)
      }
    }
  }

  private suspend fun typeAndCommit(s: String) {
    withContext(Dispatchers.EDT) {
      fixture.type(s)
      PsiDocumentManager.getInstance(fixture.project).commitDocument(fixture.editor!!.document)
    }
  }
}
