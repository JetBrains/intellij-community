// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightJavaCodeInsightTestCase

internal class ClassMirrorSurvivesFileTypeChangeTest : LightJavaCodeInsightTestCase() {
  fun testSurvival() {
    val project = project
    val fqn = "java.lang.String"

    val scope = GlobalSearchScope.allScope(project)
    val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, scope) as ClsClassImpl

    assertTrue(clazz.isValid)
    assertTrue(clazz.mirror.isValid)

    PsiManagerEx.getInstanceEx(project).fileManagerEx.processFileTypesChanged(false)

    assertTrue(clazz.isValid)
    assertTrue(clazz.mirror.isValid)
  }
}