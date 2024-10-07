// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector

import com.intellij.internal.inspector.UiInspectorUtil.ADDED_AT_STACKTRACE
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ClientProperty
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ContainerEvent
import javax.swing.CellRendererPane
import javax.swing.JComponent

private val PROPERTY_KEY = Key.create<UiInspectorContextProvider>("UiInspectorContextProvider.Key")

@ApiStatus.Internal
object UiInspectorUtil {
  @JvmField
  val ADDED_AT_STACKTRACE:  Key<Throwable> = Key.create("uiInspector.addedAt")

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

  private var shouldSaveStacktraces = false

  fun enableStacktraceSaving() {
    shouldSaveStacktraces = true
    AddedAtStacktracesCollector
  }

  fun initStacktraceSaving() {
    shouldSaveStacktraces = true
    AddedAtStacktracesCollector
  }

  fun isSaveStacktraces(): Boolean {
    return shouldSaveStacktraces || Registry.`is`("ui.inspector.save.stacktraces", false)
  }
}

private object AddedAtStacktracesCollector : AWTEventListener {
  init {
    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.CONTAINER_EVENT_MASK)
  }

  override fun eventDispatched(event: AWTEvent) {
    val child = if (event is ContainerEvent && event.id == ContainerEvent.COMPONENT_ADDED) event.child else null
    if (child is JComponent && event.source !is CellRendererPane) {
      ClientProperty.put(child, ADDED_AT_STACKTRACE, Throwable())
    }
  }
}