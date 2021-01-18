// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.fileLocation
import com.intellij.codeInsight.navigation.fileStatusAttributes
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPopupPresentation
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile

internal class PsiFileNavigationTarget(
  private val psiFile: PsiFile
) : NavigationTarget {

  override fun isValid(): Boolean = psiFile.isValid

  override fun getNavigatable(): Navigatable = psiFile

  override fun getTargetPresentation(): TargetPopupPresentation {
    val project = psiFile.project

    var builder = TargetPopupPresentation
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
    builder = builder.locationText(locationAndIcon.first, locationAndIcon.second)

    return builder.presentation()
  }
}
