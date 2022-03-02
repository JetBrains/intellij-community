// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import org.jdom.Element

class MissingJavadocMerger: InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "MissingJavadoc"
  override fun getSourceToolNames(): Array<String> = arrayOf("JavaDoc")
  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    val oldInspection = JavaDocLocalInspection()
    oldInspection.readSettings(sourceElement)
    val newInspection = MissingJavadocInspection()
    newInspection.IGNORE_ACCESSORS = oldInspection.isIgnoreSimpleAccessors
    newInspection.IGNORE_DEPRECATED_ELEMENTS = oldInspection.IGNORE_DEPRECATED
    newInspection.PACKAGE_SETTINGS = transformOptions(oldInspection.PACKAGE_OPTIONS)
    newInspection.MODULE_SETTINGS = transformOptions(oldInspection.MODULE_OPTIONS)
    newInspection.TOP_LEVEL_CLASS_SETTINGS = transformOptions(oldInspection.TOP_LEVEL_CLASS_OPTIONS)
    newInspection.INNER_CLASS_SETTINGS = transformOptions(oldInspection.INNER_CLASS_OPTIONS)
    newInspection.FIELD_SETTINGS = transformOptions(oldInspection.FIELD_OPTIONS)
    newInspection.METHOD_SETTINGS = transformOptions(oldInspection.METHOD_OPTIONS)
    newInspection.writeSettings(toolElement)
    return toolElement
  }

  private fun transformOptions(oldOption: JavaDocLocalInspection.Options): MissingJavadocInspection.Options {
    val newOption = MissingJavadocInspection.Options()
    newOption.ENABLED = oldOption.ACCESS_JAVADOC_REQUIRED_FOR != JavaDocLocalInspection.NONE
    if (oldOption.ACCESS_JAVADOC_REQUIRED_FOR.equals(JavaDocLocalInspection.NONE)) {
      newOption.MINIMAL_VISIBILITY = JavaDocLocalInspection.PUBLIC
    }
    else {
      newOption.MINIMAL_VISIBILITY = oldOption.ACCESS_JAVADOC_REQUIRED_FOR
    }
    newOption.REQUIRED_TAGS = oldOption.REQUIRED_TAGS
    return newOption
  }
}