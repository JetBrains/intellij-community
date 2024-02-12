// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class LanguageServiceWidgetItemsProvider {
  companion object {
    val EP_NAME: ExtensionPointName<LanguageServiceWidgetItemsProvider> =
      ExtensionPointName.create("com.intellij.platform.lang.lsWidget.itemsProvider")
  }

  abstract fun getWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem>

  /**
   * Example:
   *
   *    FooManager.getInstance(project).addServiceStateListener(
   *      object : ServiceStateListener {
   *        override fun serviceStateChanged() = updateWidget()
   *      },
   *      parentDisposable = widgetDisposable,
   *    )
   */
  open fun registerWidgetUpdaters(project: Project, widgetDisposable: Disposable, updateWidget: () -> Unit) {}
}
