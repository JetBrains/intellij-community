// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.distributionContent.testFramework

import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import org.jetbrains.annotations.ApiStatus.Internal

private val yaml = Yaml(
  configuration = YamlConfiguration(
    encodeDefaults = false,
    singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
    codePointLimit = 10 * 1024 * 1024, // 10 MiB, slightly lower than the max file size in our Git repo (12 MiB)
  ),
)

@Internal
fun deserializeContentData(data: String): List<FileEntry> = yaml.decodeFromString(data)

@Internal
fun deserializePluginData(data: String): List<PluginContentReport> = yaml.decodeFromString(data)

@Internal
fun serializeContentEntries(list: List<FileEntry>): String {
  return yaml.encodeToString(ListSerializer(FileEntry.serializer()), list)
}