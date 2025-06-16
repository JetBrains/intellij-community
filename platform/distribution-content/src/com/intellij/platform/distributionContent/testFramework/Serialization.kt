// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.distributionContent.testFramework

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import org.jetbrains.annotations.ApiStatus

private val yaml = Yaml(
  configuration = YamlConfiguration(
    encodeDefaults = false,
    singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
  ),
)

@ApiStatus.Internal
fun deserializeContentData(data: String): List<FileEntry> = yaml.decodeFromString(data)

@ApiStatus.Internal
fun deserializePluginData(data: String): List<PluginContentReport> = yaml.decodeFromString(data)

@ApiStatus.Internal
fun serializeContentEntries(list: List<FileEntry>): String {
  return yaml.encodeToString(ListSerializer(FileEntry.serializer()), list)
}