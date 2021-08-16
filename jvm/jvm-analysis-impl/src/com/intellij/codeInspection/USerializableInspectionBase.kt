// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.BaseInspection.formatString
import com.siyeh.ig.BaseInspection.parseString
import com.siyeh.ig.psiutils.SerializationUtils
import com.siyeh.ig.ui.UiUtils
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import javax.swing.JPanel

abstract class USerializableInspectionBase : AbstractBaseUastLocalInspectionTool() {
  var ignoreAnonymousInnerClasses = false

  private var superClassString: @NonNls String = "java.awt.Component"

  protected val superClassList: MutableList<String> = mutableListOf()

  override fun readSettings(node: Element) {
    super.readSettings(node)
    parseString(superClassString, superClassList)
  }

  override fun writeSettings(node: Element) {
    if (superClassList.isNotEmpty()) superClassString = formatString(superClassList)
    super.writeSettings(node)
  }

  override fun createOptionsPanel(): JComponent? =
    MultipleCheckboxOptionsPanel(this).apply {
      val chooserList = UiUtils.createTreeClassChooserList(
        superClassList,
        InspectionGadgetsBundle.message("ignore.classes.in.hierarchy.column.name"),
        InspectionGadgetsBundle.message("choose.class")
      )
      UiUtils.setComponentSize(chooserList, 7, 25)
      add(chooserList, "growx, wrap")
      val additionalOptions = createAdditionalOptions()
      for (additionalOption in additionalOptions) {
        val constraints = if (additionalOption is JPanel) "grow, wrap" else "growx, wrap"
        add(additionalOption, constraints)
      }
      addCheckbox(InspectionGadgetsBundle.message("ignore.anonymous.inner.classes"), "ignoreAnonymousInnerClasses")
    }

  protected fun createAdditionalOptions(): Array<JComponent> = emptyArray()

  protected fun isIgnoredSubclass(aClass: PsiClass): Boolean {
    if (SerializationUtils.isDirectlySerializable(aClass)) return false
    for (superClassName in superClassList) if (InheritanceUtil.isInheritor(aClass, superClassName)) return true
    return false
  }

  override fun getAlternativeID(): String = "serial"
}
