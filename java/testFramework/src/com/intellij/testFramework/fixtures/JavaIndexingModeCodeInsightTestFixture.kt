// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiPackage
import com.intellij.psi.impl.JavaPsiFacadeEx
import com.intellij.testFramework.TestIndexingModeSupporter

open class JavaIndexingModeCodeInsightTestFixture private constructor(delegate: JavaCodeInsightTestFixture,
                                                                      indexingMode: TestIndexingModeSupporter.IndexingMode) :
  IndexingModeCodeInsightTestFixture<JavaCodeInsightTestFixture>(delegate, indexingMode), JavaCodeInsightTestFixture {

  companion object {
    fun wrapFixture(delegate: JavaCodeInsightTestFixture,
                    indexingMode: TestIndexingModeSupporter.IndexingMode): JavaCodeInsightTestFixture {
      return if (indexingMode === TestIndexingModeSupporter.IndexingMode.SMART) {
        delegate
      }
      else JavaIndexingModeCodeInsightTestFixture(delegate, indexingMode)
    }
  }

  override fun getJavaFacade(): JavaPsiFacadeEx {
    val javaFacade = delegate.javaFacade
    ensureIndexingStatus()
    return javaFacade
  }

  override fun addClass(classText: String): PsiClass? {
    val aClass = delegate.addClass(classText)
    ensureIndexingStatus()
    return aClass
  }

  override fun findClass(name: String): PsiClass {
    val aClass = delegate.findClass(name)
    ensureIndexingStatus()
    return aClass
  }

  override fun findPackage(name: String): PsiPackage {
    val psiPackage = delegate.findPackage(name)
    ensureIndexingStatus()
    return psiPackage
  }
}