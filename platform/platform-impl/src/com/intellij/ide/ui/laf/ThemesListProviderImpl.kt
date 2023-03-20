// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.ThemesListProvider
import com.intellij.ui.ExperimentalUI
import javax.swing.UIManager

class ThemesListProviderImpl : ThemesListProvider {

  override fun getShownThemes(): List<List<UIManager.LookAndFeelInfo>> {
    val lmi = LafManager.getInstance() as? LafManagerImpl ?: return listOf()
    val result = mutableListOf<List<UIManager.LookAndFeelInfo>>()

    if (ExperimentalUI.isNewUI()) {
      result.add(lmi.getLafListForTargetUI(TargetUIType.NEW).sortedBy { it.name })
    }
    result.add((lmi.getLafListForTargetUI(TargetUIType.CLASSIC) + lmi.getLafListForTargetUI(TargetUIType.UNSPECIFIED)).sortedBy { it.name })
    return result
  }

}