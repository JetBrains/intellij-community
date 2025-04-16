// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString

private val yaml = Yaml(
  configuration = YamlConfiguration(
    encodeDefaults = false,
    singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
  ),
)

fun deserializeContentData(data: String): List<FileEntry> = yaml.decodeFromString(data)

fun deserializePluginData(data: String): List<PluginContentReport> = yaml.decodeFromString(data)

fun serializeContentEntries(list: List<FileEntry>): String {
  return yaml.encodeToString(ListSerializer(FileEntry.serializer()), list)
}