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

  abstract fun createWidgetItems(project: Project, currentFile: VirtualFile?): List<LanguageServiceWidgetItem>

  /**
   * [LanguageServiceWidgetItemsProvider] implementations should ask the Platform to update the 'Language Services' widget
   * when the state of some language service changes.
   * Typically, they register some technology-specific service state listener,
   * and call [updateWidget] function on service state change.
   * Once the [updateWidget] function is called, the Platform rebuilds the widget from scratch,
   * which means that it collects the up-to-date information by calling [createWidgetItems].
   *
   * Make sure to use the [widgetDisposable] to unregister technology-specific listeners, otherwise they will leak.
   *
   * Implementation example:
   *
   *    FooManager.getInstance(project).addServiceStateListener(
   *      listener = object : ServiceStateListener {
   *        override fun serviceStateChanged() = updateWidget()
   *      },
   *      parentDisposable = widgetDisposable,
   *    )
   */
  open fun registerWidgetUpdaters(project: Project, widgetDisposable: Disposable, updateWidget: () -> Unit) {}
}
