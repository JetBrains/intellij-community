// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element

public class JavadocDeclarationMerger: InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "JavadocDeclaration"

  override fun getSourceToolNames(): Array<String> = arrayOf("JavaDoc")

  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    val newInspection = JavadocDeclarationInspection()
    JDOMExternalizerUtil.readCustomField(sourceElement, "IGNORE_DUPLICATED_THROWS_TAGS")?.toBooleanStrictOrNull()?.also { value ->
      newInspection.IGNORE_THROWS_DUPLICATE = value
    }
    JDOMExternalizerUtil.readField(sourceElement, "IGNORE_JAVADOC_PERIOD")?.toBooleanStrictOrNull()?.also { value ->
      newInspection.IGNORE_PERIOD_PROBLEM = value
    }
    JDOMExternalizerUtil.readField(sourceElement, "IGNORE_POINT_TO_ITSELF")?.toBooleanStrictOrNull()?.also { value ->
      newInspection.IGNORE_SELF_REFS = value
    }
    JDOMExternalizerUtil.readField(sourceElement, "IGNORE_DEPRECATED")?.toBooleanStrictOrNull()?.also { value ->
      newInspection.IGNORE_DEPRECATED_ELEMENTS = value
    }
    JDOMExternalizerUtil.readField(sourceElement, "myAdditionalJavadocTags")?.also { value ->
      newInspection.ADDITIONAL_TAGS = value
    }
    newInspection.writeSettings(toolElement)
    return toolElement
  }
}