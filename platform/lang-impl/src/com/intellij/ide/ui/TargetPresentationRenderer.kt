// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.navigation.TargetPresentation
import com.intellij.openapi.project.Project
import com.intellij.ui.list.LeftRightRenderer
import com.intellij.ui.list.SearchAwareRenderer
import javax.swing.ListCellRenderer

abstract class TargetPresentationRenderer<T>(project: Project) : LeftRightRenderer<T>(), SearchAwareRenderer<T> {

  protected abstract fun getPresentation(value: T): TargetPresentation?

  override fun getItemName(item: T): String? = mainRenderer.getItemName(item)

  override val mainRenderer: SearchAwareRenderer<T> = object : TargetPresentationMainRenderer<T>(project) {
    override fun getPresentation(value: T): TargetPresentation? = this@TargetPresentationRenderer.getPresentation(value)
  }

  override val rightRenderer: ListCellRenderer<T> = object : TargetPresentationRightRenderer<T>() {
    override fun getPresentation(value: T): TargetPresentation? = this@TargetPresentationRenderer.getPresentation(value)
  }
}
