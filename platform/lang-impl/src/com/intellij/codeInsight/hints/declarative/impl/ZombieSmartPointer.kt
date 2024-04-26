// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer

internal class ZombieSmartPointer : SmartPsiElementPointer<PsiElement> {
  lateinit var projectSupp: () -> Project
  lateinit var fileSupp: () -> VirtualFile

  override fun getElement(): PsiElement? = null
  override fun getContainingFile(): PsiFile? = null
  override fun getProject(): Project = projectSupp.invoke()
  override fun getVirtualFile(): VirtualFile = fileSupp.invoke()
  override fun getRange(): Segment? = null
  override fun getPsiRange(): Segment? = null
}
