// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.NlsContexts.TabTitle
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent

@Internal
data class RegisterToolWindowTaskData(
  val id: String,
  val anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
  val component: JComponent? = null,
  val sideTool: Boolean = false,
  val canCloseContent: Boolean = true,
  val canWorkInDumbMode: Boolean = true,
  val shouldBeAvailable: Boolean = true,
  val contentFactory: ToolWindowFactory? = null,
  val icon: Icon? = null,
  val stripeTitle: Supplier<@TabTitle String>? = null,
  val hideOnEmptyContent: Boolean = false,
  val pluginDescriptor: PluginDescriptor? = null,
)

class RegisterToolWindowTask @Internal constructor(
  @get:Internal val data: RegisterToolWindowTaskData,
) {

  // compatibility constructor
  constructor(
    id: String,
    anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
    component: JComponent? = null,
    sideTool: Boolean = false,
    canCloseContent: Boolean = true,
    canWorkInDumbMode: Boolean = true,
    shouldBeAvailable: Boolean = true,
    contentFactory: ToolWindowFactory? = null,
    icon: Icon? = null,
    stripeTitle: Supplier<@TabTitle String>? = null,
  ) : this(RegisterToolWindowTaskData(
    id,
    anchor,
    component,
    sideTool,
    canCloseContent,
    canWorkInDumbMode,
    shouldBeAvailable,
    contentFactory,
    icon,
    stripeTitle,
  ))

  @get:Internal // TODO inline this
  val id: String get() = data.id

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
    fun closable(
      id: String,
      stripeTitle: Supplier<@TabTitle String>,
      icon: Icon,
      anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
    ): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, icon = icon, anchor = anchor, stripeTitle = stripeTitle)
    }

    @JvmStatic
    @JvmOverloads
    fun lazyAndClosable(
      id: String,
      contentFactory: ToolWindowFactory,
      icon: Icon,
      anchor: ToolWindowAnchor = ToolWindowAnchor.BOTTOM,
    ): RegisterToolWindowTask {
      return RegisterToolWindowTask(id, anchor = anchor, contentFactory = contentFactory, icon = icon)
    }
  }
}
