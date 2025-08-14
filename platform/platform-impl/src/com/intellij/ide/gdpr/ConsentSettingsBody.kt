// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.ConsentSettingsUi.ConsentStateSupplier
import com.intellij.ide.gdpr.ui.consents.ConsentForcedState
import com.intellij.ide.gdpr.ui.consents.ConsentForcedState.ExternallyDisabled
import com.intellij.ide.gdpr.ui.consents.ConsentGroup
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal fun createNoOptionsConsentSettings(preferencesMode: Boolean): DialogPanel {
  return panel {
    row {
      label(IdeBundle.message("gdpr.label.there.are.no.data.sharing.options.available"))
        .align(Align.CENTER)
    }.resizableRow()
  }.apply {
    addBorder(preferencesMode)
  }
}

@ApiStatus.Internal
internal fun createConsentSettings(consentMapping: MutableCollection<ConsentStateSupplier>, preferencesMode: Boolean, consents: List<Consent>): DialogPanel {
  val addCheckBox = preferencesMode || consents.size > 1
  return panel {
    val (singleConsents, consentsWithGroup) = partitionConsentsByGroupId(consents)
    addGroupConsents(consentMapping, consentsWithGroup, addCheckBox)
    addSingleConsents(consentMapping, singleConsents, addCheckBox)
    if (!ConsentOptions.getInstance().isEAP) {
      row {
        comment(IdeBundle.message("gdpr.hint.text.apply.to.all.installed.products", ApplicationInfoImpl.getShadowInstance().getShortCompanyName()))
      }
    }
    if (!preferencesMode) {
      row {
        comment(IdeBundle.message("gdpr.hint.text.you.can.always.change.this.behavior", ShowSettingsUtil.getSettingsMenuName()))
      }
    }
  }.apply {
    background = if (preferencesMode) UIUtil.getPanelBackground() else UIUtil.getEditorPaneBackground()
    addBorder(preferencesMode)
  }
}

internal fun partitionConsentsByGroupId(consents: List<Consent>): Pair<List<Consent>, List<ConsentGroup>> {
  var ungroupedConsents = consents.toList()
  val consentGroups = ConsentGroup.CONSENT_GROUP_MAPPING.mapNotNull { (groupId, predicate) ->
    val matchingConsents = ungroupedConsents.filter { predicate(it) }
    if (matchingConsents.isEmpty()) null
    else {
      ungroupedConsents = ungroupedConsents.filterNot(predicate)
      ConsentGroup(groupId, matchingConsents)
    }
  }
  return ungroupedConsents to consentGroups
}

private fun Panel.addSingleConsents(
  consentMapping: MutableCollection<ConsentStateSupplier>,
  singleConsents: List<Consent>,
  addCheckBox: Boolean
) {
  singleConsents.forEach { consent ->
    val supplier = createConsentElement(consent, addCheckBox)
    consentMapping.add(supplier)
  }
}

private fun Panel.addGroupConsents(
  consentMapping: MutableCollection<ConsentStateSupplier>,
  consentsGroups: List<ConsentGroup>,
  addCheckBox: Boolean
) {
  consentsGroups.forEach { consentGroup ->
    if (consentGroup.consents.size > 1) {
      val suppliers = createGroupConsentsElement(consentGroup)
      consentMapping.addAll(suppliers)
    } else {
      addSingleConsents(consentMapping, consentGroup.consents, addCheckBox)
    }
  }
}

private fun DialogPanel.addBorder(preferencesMode: Boolean) {
  if (!preferencesMode) {
    border = JBUI.Borders.empty(10)
  }
}

private fun Panel.createConsentElement(consent: Consent, addCheckBox: Boolean): ConsentStateSupplier {
  val consentUi = ConsentSettingsUi.getConsentUi(consent)
  val forcedState = consentUi.getForcedState()
  lateinit var result: ConsentStateSupplier

  val row = if (addCheckBox) {
    row {
      val cb = checkBox(consentUi.getCheckBoxText())
        .comment(processCheckboxComment(consentUi.getCheckBoxCommentText()))
        .selected(consent.isAccepted)
        .component
      result = createConsentStateSupplier(forcedState, cb, consent)
    }
  }
  else {
    result = ConsentStateSupplier(consent) { true }
    row {
      cell(ConsentSettingsUi.createSingleConsent(consent))
        .align(AlignX.FILL)
    }
  }

  val warning = getWarning(forcedState)
  if (warning == null) {
    row.bottomGap(BottomGap.SMALL)
  }
  else {
    addWarningRow(warning)
  }

  return result
}

private fun Panel.createGroupConsentsElement(consentGroup: ConsentGroup): List<ConsentStateSupplier> {
  val consentGroupUI = ConsentSettingsUi.getConsentGroupUi(consentGroup)
  if (consentGroupUI == null) return emptyList()
  val results: MutableList<ConsentStateSupplier> = mutableListOf()

  row {
    comment(processCheckboxComment(consentGroupUI.commentText), maxLineLength = DEFAULT_COMMENT_WIDTH)
  }.bottomGap(BottomGap.SMALL)

  for ((consentUI, consent) in consentGroupUI.consentUis zip consentGroup.consents) {
    val forcedState = consentUI.getForcedState()
    indent {
      row {
        val cb = checkBox(consentUI.getCheckBoxText())
          .comment(processCheckboxComment(consentUI.getCheckBoxCommentText()))
          .selected(consent.isAccepted)
          .component
        results.add(createConsentStateSupplier(forcedState, cb, consent))
      }.bottomGap(BottomGap.SMALL)
    }
  }

  consentGroupUI.forcedStateDescription?.let { description ->
    indent {
      addWarningRow(description)
    }
  }
  return results
}

private fun createConsentStateSupplier(
  forcedState: ConsentForcedState?,
  cb: JBCheckBox,
  consent: Consent,
): ConsentStateSupplier = when (forcedState) {
  is ExternallyDisabled -> {
    cb.isEnabled = false
    cb.isSelected = false
    ConsentStateSupplier(consent) { consent.isAccepted }
  }

  is ConsentForcedState.AlwaysEnabled -> {
    cb.isEnabled = false
    cb.isSelected = true
    ConsentStateSupplier(consent) { consent.isAccepted }
  }

  else -> {
    ConsentStateSupplier(consent) { cb.isSelected }
  }
}

private fun getWarning(forcedState: ConsentForcedState?): @NlsSafe String? = when (forcedState) {
  is ExternallyDisabled -> forcedState.description
  is ConsentForcedState.AlwaysEnabled -> forcedState.description
  else -> null
}

private fun Panel.addWarningRow(warning: @NlsSafe String) {
  row {
    icon(AllIcons.General.Warning)
      .gap(RightGap.SMALL)
      .align(AlignY.TOP)
    comment(warning, maxLineLength = DEFAULT_COMMENT_WIDTH)
  }.bottomGap(BottomGap.SMALL)
}

private fun processCheckboxComment(text: @NlsSafe String): @NlsSafe String {
  val paragraphs = text.split("\n")
  if (paragraphs.size <= 1) {
    return text
  }

  return paragraphs.joinToString(prefix = "<p>", separator = "<p style=\"margin-top:${ConsentSettingsUi.getParagraphSpace()}px;\">")
}