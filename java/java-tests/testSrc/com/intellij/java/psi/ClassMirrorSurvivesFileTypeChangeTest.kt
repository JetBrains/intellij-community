// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class ClassMirrorSurvivesFileTypeChangeTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  @Test
  fun testSurvival(): Unit = timeoutRunBlocking {
    val project = projectFixture.get().project
    val fqn = "java.lang.String"

    val scope = GlobalSearchScope.allScope(project)
    val clazz = readAction { JavaPsiFacade.getInstance(project).findClass(fqn, scope) as ClsClassImpl }

    readAction {
      BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation {
        assertTrue(clazz.isValid)
        assertTrue(clazz.mirror.isValid)
      }
    }

    withContext(Dispatchers.EDT) {
      PsiManagerEx.getInstanceEx(project).fileManagerEx.processFileTypesChanged(false)
    }

    readAction {
      BinaryFileTypeDecompilers.getInstance().allowDecompilerSlowOperation {
        assertTrue(clazz.isValid)
        assertTrue(clazz.mirror.isValid)
      }
    }
  }
}