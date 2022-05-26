// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.ex.InspectionElementsMergerBase
import org.jdom.Element

class JavadocDeclarationMerger: InspectionElementsMergerBase() {
  override fun getMergedToolName(): String = "JavadocDeclaration"

  override fun getSourceToolNames(): Array<String> = arrayOf("JavaDoc")

  override fun transformElement(sourceToolName: String, sourceElement: Element, toolElement: Element): Element {
    val oldInspection = JavaDocLocalInspection()
    oldInspection.readSettings(sourceElement)
    val newInspection = JavadocDeclarationInspection()
    newInspection.IGNORE_THROWS_DUPLICATE = oldInspection.isIgnoreDuplicatedThrows
    newInspection.IGNORE_PERIOD_PROBLEM = oldInspection.IGNORE_JAVADOC_PERIOD
    newInspection.IGNORE_SELF_REFS = oldInspection.IGNORE_POINT_TO_ITSELF
    newInspection.ADDITIONAL_TAGS = oldInspection.myAdditionalJavadocTags
    newInspection.writeSettings(toolElement)
    return toolElement
  }
}