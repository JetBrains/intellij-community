package org.jetbrains.jewel.buildlogic.palette

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IntellijThemeDescriptor(
    val name: String,
    val author: String = "",
    val dark: Boolean = false,
    val editorScheme: String,
    val colors: Map<String, String> = mapOf(),
    val ui: Map<String, JsonElement> = mapOf()
)
