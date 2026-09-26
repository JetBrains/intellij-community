// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element

public class MissingJavadocMerger: InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "MissingJavadoc"
  override fun getSourceToolNames(): Array<String> = arrayOf("JavaDoc")
  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    val newInspection = MissingJavadocInspection()
    val ignoreAccessors = JDOMExternalizerUtil.readCustomField(sourceElement, "IGNORE_ACCESSORS")?.toBooleanStrictOrNull()
    if (ignoreAccessors != null) {
      newInspection.IGNORE_ACCESSORS = ignoreAccessors
    }
    val ignoreDeprecated = JDOMExternalizerUtil.readField(sourceElement, "IGNORE_DEPRECATED")?.toBooleanStrictOrNull()
    if (ignoreDeprecated != null) {
      newInspection.IGNORE_DEPRECATED_ELEMENTS = ignoreDeprecated
    }
    readOptions(newInspection.PACKAGE_SETTINGS, sourceElement)
    readOptions(newInspection.MODULE_SETTINGS, JDOMExternalizerUtil.readOption(sourceElement, "MODULE_OPTIONS"))
    readOptions(newInspection.TOP_LEVEL_CLASS_SETTINGS, getFieldValue(sourceElement, "TOP_LEVEL_CLASS_OPTIONS"))
    readOptions(newInspection.INNER_CLASS_SETTINGS, getFieldValue(sourceElement, "INNER_CLASS_OPTIONS"))
    readOptions(newInspection.FIELD_SETTINGS, getFieldValue(sourceElement, "FIELD_OPTIONS"))
    readOptions(newInspection.METHOD_SETTINGS, getFieldValue(sourceElement, "METHOD_OPTIONS"))
    newInspection.writeSettings(toolElement)
    return toolElement
  }

  override fun writeMergedContent(toolElement: Element): Boolean {
    return true
  }

  override fun isEnabledByDefault(sourceToolName: String): Boolean {
    return false
  }

  private fun getFieldValue(sourceElement: Element, field: String) = JDOMExternalizerUtil.readOption(sourceElement, field)?.getChild("value")

  private fun readOptions(options: MissingJavadocInspection.Options, element: Element?)  {
    //default scope in previous inspection was "none" which is equivalent to disabled settings
    options.ENABLED = false
    if (element == null) return
    val requiredTags = JDOMExternalizerUtil.readField(element, "REQUIRED_TAGS")
    if (requiredTags != null) {
      options.REQUIRED_TAGS = requiredTags
    }

    val requiredAccess = JDOMExternalizerUtil.readField(element, "ACCESS_JAVADOC_REQUIRED_FOR")
    if (requiredAccess != null) {
      options.ENABLED = requiredAccess != "none"
      options.MINIMAL_VISIBILITY = requiredAccess.takeIf { it != "none" } ?: MissingJavadocInspection.PUBLIC
    }
  }
}