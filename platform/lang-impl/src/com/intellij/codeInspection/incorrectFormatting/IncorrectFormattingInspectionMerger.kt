// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import org.jdom.Element

class IncorrectFormattingInspectionMerger : InspectionElementsMergerBase() {

  override fun getMergedToolName(): String {
    return "IncorrectFormatting"
  }

  override fun getSourceToolNames(): Array<String> {
    return arrayOf("Reformat")
  }

  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    if (sourceElement.getAttributeValue("enabled", "false").toBoolean()) {
      toolElement.addContent(
        Element("option")
          .setAttribute("name", "kotlinOnly")
          .setAttribute("value", "true")
      )
    }
    return toolElement
  }

}
