// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.actions

import com.intellij.ide.IdeView
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.impl.psiDirectories
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.containers.toArray

internal class NavBarIdeView(private val selectedItems: Lazy<List<NavBarItem>>) : IdeView {

  override fun getDirectories(): Array<out PsiDirectory> {
    return selectedItems.value.flatMap {
      it.psiDirectories()
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
