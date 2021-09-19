package com.intellij.feedback.state.createdProject

import com.intellij.util.xmlb.Converter
import org.gradle.internal.impldep.com.google.gson.GsonBuilder
import org.gradle.internal.impldep.com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class NewProjectInfoConverter : Converter<MutableList<NewProjectInfoEntry>>() {

  private val gson = GsonBuilder().create()

  override fun fromString(value: String): MutableList<NewProjectInfoEntry>? {
    val type: Type = object : TypeToken<MutableList<NewProjectInfoEntry>?>() {}.type
    return gson.fromJson(value, type)
  }

  override fun toString(value: MutableList<NewProjectInfoEntry>): String? {
    return gson.toJson(value)
  }
}