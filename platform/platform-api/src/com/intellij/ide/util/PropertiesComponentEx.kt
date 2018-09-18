// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util

import com.intellij.openapi.project.Project
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * @author yole
 */
private fun propComponent(project: Project?): PropertiesComponent {
  return if (project == null) PropertiesComponent.getInstance() else PropertiesComponent.getInstance(project)
}

private fun propName(thisRef: Any, property: KProperty<*>) = thisRef::class.qualifiedName + "." + property.name

private class PropertiesComponentIntProperty(private val project: Project?, private val defaultValue: Int) : ReadWriteProperty<Any, Int> {
  override fun getValue(thisRef: Any, property: KProperty<*>): Int {
    return propComponent(project).getInt(propName(thisRef, property), defaultValue)
  }

  override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) {
    propComponent(project).setValue(propName(thisRef, property), value, defaultValue)
  }
}

private class PropertiesComponentStringProperty(private val project: Project?, private val defaultValue: String) : ReadWriteProperty<Any, String> {
  override fun getValue(thisRef: Any, property: KProperty<*>): String {
    return propComponent(project).getValue(propName(thisRef, property), defaultValue)
  }

  override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
    propComponent(project).setValue(propName(thisRef, property), value, defaultValue)
  }
}

private class PropertiesComponentBooleanProperty(private val project: Project?, private val defaultValue: Boolean) : ReadWriteProperty<Any, Boolean> {
  override fun getValue(thisRef: Any, property: KProperty<*>): Boolean {
    return propComponent(project).getBoolean(propName(thisRef, property), defaultValue)
  }

  override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
    propComponent(project).setValue(propName(thisRef, property), value, defaultValue)
  }
}

fun propComponentProperty(project: Project? = null, defaultValue: Int = 0): ReadWriteProperty<Any, Int> =
  PropertiesComponentIntProperty(project, defaultValue)

fun propComponentProperty(project: Project? = null, defaultValue: String = ""): ReadWriteProperty<Any, String> =
  PropertiesComponentStringProperty(project, defaultValue)

fun propComponentProperty(project: Project? = null, defaultValue: Boolean = false): ReadWriteProperty<Any, Boolean> =
  PropertiesComponentBooleanProperty(project, defaultValue)
