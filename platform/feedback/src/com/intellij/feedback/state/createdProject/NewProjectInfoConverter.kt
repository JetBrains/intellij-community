package com.intellij.feedback.state.createdProject

import com.intellij.util.xmlb.Converter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


class NewProjectInfoConverter : Converter<List<NewProjectInfoEntry>>() {

  override fun fromString(value: String): List<NewProjectInfoEntry> {
    return Json.decodeFromString(value)
  }

  override fun toString(value: List<NewProjectInfoEntry>): String {
    return Json.encodeToString(value)
  }
}