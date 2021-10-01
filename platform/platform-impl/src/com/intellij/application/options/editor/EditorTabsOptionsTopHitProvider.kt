// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor

import com.intellij.ide.ui.OptionsSearchTopHitProvider

class EditorTabsOptionsTopHitProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId() = ID

  override fun getOptions() = listOf(
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
