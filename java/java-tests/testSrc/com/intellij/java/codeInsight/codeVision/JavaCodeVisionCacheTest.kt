// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.JavaCodeInsightTestCase
import com.intellij.codeInsight.daemon.impl.UsagesCountManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope

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

  private fun configureAndOpenLocalFile() {
    configureByFiles(null, "${getTestDirectory()}/Test.java", "${getTestDirectory()}/ExternalUsage.java", )
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