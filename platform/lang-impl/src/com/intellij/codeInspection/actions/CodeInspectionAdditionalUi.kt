// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.actions

import com.intellij.CommonBundle
import com.intellij.application.options.schemes.SchemesCombo
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CodeInspectionAdditionalUi {
  val browseProfilesCombo: SchemesCombo<InspectionProfileImpl> = object : SchemesCombo<InspectionProfileImpl>() {
    override fun supportsProjectSchemes(): Boolean {
      return true
    }

    override fun isProjectScheme(profile: InspectionProfileImpl): Boolean {
      return profile.isProjectLevel
    }

    override fun getSchemeAttributes(profile: InspectionProfileImpl): SimpleTextAttributes {
      return SimpleTextAttributes.REGULAR_ATTRIBUTES
    }
  }
  lateinit var link: ActionLink
  val panel: DialogPanel = panel {
      row(InspectionsBundle.message("inspection.action.profile.label")) {
        cell(browseProfilesCombo)
          .align(AlignX.FILL)
          .resizableColumn()
        link = link(CommonBundle.message("action.text.configure.ellipsis")) {}.component
      }
    }
}