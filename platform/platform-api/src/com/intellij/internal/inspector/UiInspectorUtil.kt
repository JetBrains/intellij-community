// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClientProperty
import java.awt.Component
import javax.swing.JComponent

private val PROPERTY_KEY = Key.create<UiInspectorContextProvider>("UiInspectorContextProvider.Key")

object UiInspectorUtil {
  @JvmStatic
  fun registerProvider(component: JComponent, provider: UiInspectorContextProvider) {
    ClientProperty.put(component, PROPERTY_KEY, provider)
  }

  @JvmStatic
  fun getProvider(component: Any): UiInspectorContextProvider? {
    return when (component) {
      is UiInspectorContextProvider -> component
      is JComponent -> ClientProperty.get(component, PROPERTY_KEY)
      else -> null
    }
  }

  @JvmStatic
  fun getComponentName(component: Component): String {
    var name = getClassName(component)
    val componentName = component.name
    if (StringUtil.isNotEmpty(componentName)) {
      name += " \"$componentName\""
    }
    return name
  }

  @JvmStatic
  fun getClassName(value: Any): String {
    val clazz0 = value.javaClass
    val clazz = if (clazz0.isAnonymousClass) clazz0.superclass else clazz0
    return clazz.simpleName
  }

  @JvmStatic
  fun getClassPresentation(value: Any?): String {
    if (value == null) return "[null]"
    return getClassPresentation(value.javaClass)
  }

  @JvmStatic
  fun getClassPresentation(clazz0: Class<*>): String {
    val clazz = if (clazz0.isAnonymousClass) clazz0.superclass else clazz0
    val simpleName = clazz.simpleName
    return simpleName + " (" + clazz.packageName + ")"
  }
}
