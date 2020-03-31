// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationBundle.message
import com.intellij.ui.layout.*

// @formatter:off
const val ID = "editor.preferences.tabs"

internal val ui = UISettings.instance.state

internal val showDirectoryForNonUniqueFilenames  get() = CheckboxDescriptor(message("checkbox.show.directory.for.non.unique.files"), ui::showDirectoryForNonUniqueFilenames)
internal val markModifiedTabsWithAsterisk        get() = CheckboxDescriptor(message("checkbox.mark.modified.tabs.with.asterisk"), ui::markModifiedTabsWithAsterisk)
internal val showTabsTooltips                    get() = CheckboxDescriptor(message("checkbox.show.tabs.tooltips"), ui::showTabsTooltips)
internal val showFileIcon                        get() = CheckboxDescriptor(message("checkbox.show.file.icon.in.editor.tabs"), ui::showFileIconInTabs)
internal val showFileExtension                   get() = CheckboxDescriptor(message("checkbox.show.file.extension.in.editor.tabs"), PropertyBinding({ !ui.hideKnownExtensionInTabs }, { ui.hideKnownExtensionInTabs = !it }))
internal val hideTabsIfNeeded                    get() = CheckboxDescriptor(message("checkbox.editor.scroll.if.need"), ui::hideTabsIfNeeded)
internal val sortTabsAlphabetically              get() = CheckboxDescriptor(message("checkbox.sort.tabs.alphabetically"), ui::sortTabsAlphabetically)
internal val openTabsAtTheEnd                    get() = CheckboxDescriptor(message("checkbox.open.new.tabs.at.the.end"), ui::openTabsAtTheEnd)
internal val reuseNotModifiedTabs                get() = CheckboxDescriptor(message("checkbox.smart.tab.reuse"), ui::reuseNotModifiedTabs, message("checkbox.smart.tab.reuse.inline.help"))
internal val scrollTabLayoutInEditor             get() = CheckboxDescriptor(message("checkbox.editor.tabs.in.single.row"), ui::scrollTabLayoutInEditor)
internal val useSmallFont                        get() = CheckboxDescriptor(message("checkbox.use.small.font.for.labels"), ui::useSmallLabelsOnTabs)
