// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.fileLocation
import com.intellij.codeInsight.navigation.fileStatusAttributes
import com.intellij.model.Pointer
import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.createSmartPointer

internal class PsiFileNavigationTarget(
  private val psiFile: PsiFile
) : NavigationTarget {

  override fun createPointer(): Pointer<out NavigationTarget> = Pointer.delegatingPointer(
    psiFile.createSmartPointer(), PsiFileNavigationTarget::class.java, ::PsiFileNavigationTarget
  )

  override fun getTargetPresentation(): TargetPresentation {
    val project = psiFile.project

    var builder = TargetPresentation
      .builder(psiFile.name)
      .icon(psiFile.getIcon(0))
      .containerText(psiFile.parent?.virtualFile?.presentableUrl)

    val file = psiFile.virtualFile
               ?: return builder.presentation()

    builder = builder
      .backgroundColor(VfsPresentationUtil.getFileBackgroundColor(project, file))
      .presentableTextAttributes(fileStatusAttributes(project, file)) // apply file error and file status highlighting to file name

    val locationAndIcon = fileLocation(project, file)
                          ?: return builder.presentation()
    @Suppress("HardCodedStringLiteral")
    builder = builder.locationText(locationAndIcon.text, locationAndIcon.icon)

    return builder.presentation()
  }

  override fun navigationRequest(): NavigationRequest? {
    return psiFile.navigationRequest()
  }
}
