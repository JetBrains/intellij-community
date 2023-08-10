// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsState
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.ui.ExperimentalUI

internal const val EDITOR_TABS_OPTIONS_ID: String = "editor.preferences.tabs"

private val ui: UISettingsState
  get() = UISettings.getInstance().state

internal val showDirectoryForNonUniqueFilenames: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.show.directory.for.non.unique.files"), ui::showDirectoryForNonUniqueFilenames)
internal val markModifiedTabsWithAsterisk: CheckboxDescriptor
  get() {
    val text = if (ExperimentalUI.isNewUI()) {
      message("checkbox.mark.modified.tabs")
    }
    else message("checkbox.mark.modified.tabs.with.asterisk")
    return CheckboxDescriptor(text, ui::markModifiedTabsWithAsterisk)
  }
internal val showTabsTooltips: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.show.tabs.tooltips"), ui::showTabsTooltips)
internal val showFileIcon: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.show.file.icon.in.editor.tabs"), ui::showFileIconInTabs)
internal val showFileExtension: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.show.file.extension.in.editor.tabs"), { !ui.hideKnownExtensionInTabs }, { ui.hideKnownExtensionInTabs = !it })
internal val hideTabsIfNeeded: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.editor.scroll.if.need"), ui::hideTabsIfNeeded)
internal val showPinnedTabsInASeparateRow: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.show.pinned.tabs.in.a.separate.row"), ui::showPinnedTabsInASeparateRow)
internal val sortTabsAlphabetically: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.sort.tabs.alphabetically"), ui::sortTabsAlphabetically)
internal val openTabsAtTheEnd: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.open.new.tabs.at.the.end"), ui::openTabsAtTheEnd)
internal val showTabsInOneRow: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.editor.tabs.in.single.row"), ui::scrollTabLayoutInEditor)
internal val openInPreviewTabIfPossible: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.smart.tab.preview"), ui::openInPreviewTabIfPossible,
                             message("checkbox.smart.tab.preview.inline.help"))
internal val useSmallFont: CheckboxDescriptor
  get() = CheckboxDescriptor(message("checkbox.use.small.font.for.labels"), ui::useSmallLabelsOnTabs)
