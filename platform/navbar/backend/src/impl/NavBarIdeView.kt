// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.impl

import com.intellij.ide.IdeView
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.model.Pointer
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.containers.toArray

internal class NavBarIdeView(private val selection: List<Pointer<out NavBarItem>>) : IdeView {

  override fun getDirectories(): Array<out PsiDirectory> {
    return selection.flatMap {
      it.dereference()?.psiDirectories() ?: emptyList()
    }.toArray(PsiDirectory.EMPTY_ARRAY)
  }

  override fun getOrChooseDirectory(): PsiDirectory? {
    return DirectoryChooserUtil.getOrChooseDirectory(this)
  }

  override fun selectElement(element: PsiElement) {
    if (element is Navigatable) {
      val navigatable = element as Navigatable
      if (navigatable.canNavigate()) {
        (element as Navigatable).navigate(true)
      }
    }
  }
}
