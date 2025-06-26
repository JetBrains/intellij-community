// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.ui.tabs.TabInfo.Companion.ICON
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
interface TabInfoIconHolder {
  fun setIcon(icon: Icon?)
  fun getIcon(): Icon?
  companion object {
    @JvmStatic
    fun default(owner: TabInfo): TabInfoIconHolder {
      return object : TabInfoIconHolder {
        private var icon: Icon? = null

        override fun setIcon(icon: Icon?) {
          val old = this.icon
          if (old != icon) {
            this.icon = icon
            owner.changeSupport.firePropertyChange(ICON, old, icon)
          }
        }

        override fun getIcon(): Icon? {
          return icon
        }
      }
    }
  }
}