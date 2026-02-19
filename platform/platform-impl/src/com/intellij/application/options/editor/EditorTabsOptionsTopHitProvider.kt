// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EditorTabsOptionsTopHitProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId(): String = EDITOR_TABS_OPTIONS_ID

  override fun getOptions(): List<BooleanOptionDescription> = listOf(
    showDirectoryForNonUniqueFilenames
    , markModifiedTabsWithAsterisk
    , showTabsTooltips
    , showFileExtension
    , hideTabsIfNeeded
    , sortTabsAlphabetically
    , openTabsAtTheEnd
    , openInPreviewTabIfPossible
    , useSmallFont).map(CheckboxDescriptor::asUiOptionDescriptor) + tabPlacementsOptionDescriptors + closeButtonPlacementOptionDescription()
}
