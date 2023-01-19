// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInsight.options.JavaClassValidator
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.*
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.BaseInspection.formatString
import com.siyeh.ig.BaseInspection.parseString
import com.siyeh.ig.psiutils.SerializationUtils
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.UElement

abstract class USerializableInspectionBase(vararg hint: Class<out UElement>) : AbstractBaseUastLocalInspectionTool(*hint) {
  @JvmField
  var ignoreAnonymousInnerClasses = false

  @JvmField
  var superClassString: @NonNls String = "java.awt.Component"

  private val superClassList: MutableList<String> = mutableListOf<String>().also { parseString(superClassString, it) }

  override fun readSettings(node: Element) {
    super.readSettings(node)
    parseString(superClassString, superClassList)
  }

  override fun writeSettings(node: Element) {
    if (superClassList.isNotEmpty()) superClassString = formatString(superClassList)
    super.writeSettings(node)
  }

  override fun getOptionsPane(): OptPane = pane(
    stringList("superClassList", InspectionGadgetsBundle.message("ignore.classes.in.hierarchy.column.name"),
               JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.class"))),
    checkbox("ignoreAnonymousInnerClasses", InspectionGadgetsBundle.message("ignore.anonymous.inner.classes"))
  )

  protected fun isIgnoredSubclass(aClass: PsiClass): Boolean {
    if (SerializationUtils.isDirectlySerializable(aClass)) return false
    for (superClassName in superClassList) if (InheritanceUtil.isInheritor(aClass, superClassName)) return true
    return false
  }

  override fun getAlternativeID(): String = "serial"
}
