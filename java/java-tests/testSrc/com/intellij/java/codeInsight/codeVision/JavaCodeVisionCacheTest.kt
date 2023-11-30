// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.codeInsight.daemon.impl.UsageCounterConfiguration
import com.intellij.codeInsight.daemon.impl.UsagesCountManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger

class JavaCodeVisionCacheTest : JavaCodeInsightTestCase() {

  override fun getTestDataPath(): String {
    return JavaTestUtil.getJavaTestDataPath() + "/codeVision/"
  }

  fun testChangedMethodName() {
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("random")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 0)
  }

  fun testChangedClassName() {
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("random")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 2)
  }

  fun testAddedExternalUsage() {
    configureAndOpenExternalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("Test.test();")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 4)
  }

  fun testAddedLocalUsage() {
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    val initialUsages = countUsages(testMember)
    require(initialUsages == 3)
    typeAndCommit("test();")
    val currentUsages = countUsages(testMember)
    require(currentUsages == 4)
  }

  fun testUsagesNotRecomputed() {
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
    val usagesCountManager = UsagesCountManager(project, testUsagesCounter)
    Disposer.register(testRootDisposable, usagesCountManager)
    require(localComputations.get() == 0)
    require(externalComputations.get() == 0)
    configureAndOpenLocalFile()
    val testMember = findTestMember()
    usagesCountManager.countMemberUsages(testMember.containingFile, testMember)
    require(localComputations.get() == 1)
    require(externalComputations.get() == 1)
    typeAndCommit("test();")
    usagesCountManager.countMemberUsages(testMember.containingFile, testMember)
    require(localComputations.get() == 2)
    require(externalComputations.get() == 1)
  }

  private fun configureAndOpenLocalFile() {
    configureByFiles(null, "${getTestDirectory()}/Test.java", "${getTestDirectory()}/ExternalUsage.java")
  }

  private fun configureAndOpenExternalFile() {
    configureByFiles(null, "${getTestDirectory()}/ExternalUsage.java", "${getTestDirectory()}/Test.java")
  }

  private fun getTestDirectory(): String = getTestName(true)

  private fun findTestMember(): PsiMember {
    val testClass = JavaPsiFacade.getInstance(project).findClass("Test", GlobalSearchScope.allScope(project))
    val externalClass = JavaPsiFacade.getInstance(project).findClass("ExternalUsage", GlobalSearchScope.allScope(project))
    require(externalClass != null)
    val member: PsiMember? = testClass?.findMethodsByName("test", false)?.firstOrNull()
    require(member != null)
    return member
  }

  private fun countUsages(member: PsiMember): Int {
    return UsagesCountManager.getInstance(project).countMemberUsages(member.containingFile, member)
  }

  private fun typeAndCommit(s: String) {
    type(s)
    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
  }
}