// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.util.NlsContexts
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent

data class RegisterToolWindowTask(
  val id: String,
  val anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
  val component: JComponent? = null,
  val sideTool: Boolean = false,
  val canCloseContent: Boolean = true,
  val canWorkInDumbMode: Boolean = true,
  val shouldBeAvailable: Boolean = true,
  val contentFactory: ToolWindowFactory? = null,
  val icon: Icon? = null,
  val stripeTitle: Supplier<@NlsContexts.TabTitle String>? = null
) {
  // for Java clients
  companion object {
    @JvmStatic
    @JvmOverloads
    fun notClosable(id: String, anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, canCloseContent = false, anchor = anchor)
    }

    @JvmStatic
    @JvmOverloads
    fun notClosable(id: String, icon: Icon, anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, canCloseContent = false, icon = icon, anchor = anchor)
    }

    @JvmStatic
    @JvmOverloads
    fun closable(id: String, icon: Icon, anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, icon = icon, anchor = anchor)
    }

    @JvmStatic
    @JvmOverloads
    fun closable(id: String, 
                 stripeTitle: Supplier<@NlsContexts.TabTitle String>,
                 icon: Icon, 
                 anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, icon = icon, anchor = anchor, stripeTitle = stripeTitle)
    }

    @JvmStatic
    @JvmOverloads
    fun closableSecondary(id: String,
                          stripeTitle: Supplier<@NlsContexts.TabTitle String>,
                          icon: Icon,
                          anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, anchor = anchor, icon = icon, sideTool = true, stripeTitle = stripeTitle)
    }

    @JvmStatic
    @JvmOverloads
    fun lazyAndClosable(id: String,
                        contentFactory: ToolWindowFactory,
                        icon: Icon,
                        anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, anchor = anchor, contentFactory = contentFactory, icon = icon)
    }

    @JvmStatic
    fun lazyAndClosableWithStripeTitle(id: String,
                                       contentFactory: ToolWindowFactory,
                                       icon: Icon,
                                       stripeTitle: Supplier<@NlsContexts.TabTitle String>?): RegisterToolWindowTask {
      return RegisterToolWindowTask(id = id,
                                    anchor = ToolWindowAnchor.BOTTOM,
                                    contentFactory = contentFactory,
                                    icon = icon,
                                    stripeTitle = stripeTitle)
    }

    @JvmStatic
    @JvmOverloads
    fun lazyAndNotClosable(id: String,
                           contentFactory: ToolWindowFactory,
                           icon: Icon,
                           anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, canCloseContent = false, anchor = anchor, contentFactory = contentFactory, icon = icon)
    }
  }
}
