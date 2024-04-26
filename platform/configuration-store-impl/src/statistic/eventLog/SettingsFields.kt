// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistic.eventLog

import com.intellij.internal.statistic.eventLog.events.EventFields

internal class SettingsFields {
  companion object {
    enum class Types(val value: String) {
      BOOl("bool"),
      INT("int"),
      FLOAT("float"),
      ENUM("enum"),
      STRING("string")
    }

    @JvmField
    val COMPONENT_FIELD = EventFields.StringValidatedByCustomRule("component", SettingsComponentNameValidator::class.java)

    @JvmField
    val NAME_FIELD = EventFields.StringValidatedByCustomRule("name", SettingsComponentNameValidator::class.java)

    @JvmField
    val DEFAULT_PROJECT_FIELD = EventFields.Boolean("default_project")

    // used only in tests, not part of the scheme
    @JvmField
    val DEFAULT_FIELD = EventFields.Boolean("default")

    @JvmField
    val ID_FIELD = EventFields.Int("id")

    @JvmField
    val TYPE_FIELD = EventFields.Enum<Types>("type", transform = { it.value })

    @JvmField
    val VALUE_FIELD = EventFields.StringValidatedByCustomRule("value", SettingsValueValidator::class.java)

    @JvmField
    val PLUGIN_INFO_FIELD = EventFields.PluginInfo
  }
}