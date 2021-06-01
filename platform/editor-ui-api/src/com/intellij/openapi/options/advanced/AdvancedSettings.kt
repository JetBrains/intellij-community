// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import com.intellij.util.messages.Topic.BroadcastDirection

enum class AdvancedSettingType { Int, Bool, String, Enum }

abstract class AdvancedSettings  {
  protected abstract fun getSetting(id: String): Any
  abstract fun setSetting(id: String, value: Any, expectType: AdvancedSettingType)

  companion object {
    @JvmStatic
    fun getInstance(): AdvancedSettings = ApplicationManager.getApplication().getService(AdvancedSettings::class.java)

    @JvmStatic
    fun getInstanceIfCreated(): AdvancedSettings? = ApplicationManager.getApplication()?.getServiceIfCreated(AdvancedSettings::class.java)

    @JvmStatic
    fun getBoolean(id: String): Boolean = getInstance().getSetting(id) as Boolean

    @JvmStatic
    fun getInt(id: String): Int = getInstance().getSetting(id) as Int

    @JvmStatic
    fun getString(id: String): String = getInstance().getSetting(id) as String

    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun <T: Enum<T>> getEnum(id: String, enumClass: Class<T>): T = enumClass.cast(getInstance().getSetting(id))

    @JvmStatic
    fun setBoolean(id: String, value: Boolean) {
      getInstance().setSetting(id, value, AdvancedSettingType.Bool)
    }

    @JvmStatic
    fun setInt(id: String, value: Int) {
      getInstance().setSetting(id, value, AdvancedSettingType.Int)
    }

    @JvmStatic
    fun setString(id: String, value: String) {
      getInstance().setSetting(id, value, AdvancedSettingType.String)
    }

    @JvmStatic
    fun setEnum(id: String, value: Enum<*>) {
      getInstance().setSetting(id, value, AdvancedSettingType.Enum)
    }
  }
}

interface AdvancedSettingsChangeListener {
  fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any)

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic(AdvancedSettingsChangeListener::class.java, BroadcastDirection.NONE)
  }
}
