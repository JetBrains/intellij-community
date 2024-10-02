// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.scratch.ScratchesNamedScope

import com.intellij.openapi.fileEditor.impl.OpenFilesScope
import com.intellij.psi.search.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
class ScopeIdMapperImpl : ScopeIdMapper() {
  @Suppress("HardCodedStringLiteral")
  override fun getPresentableScopeName(scopeId: String): String =
    when (scopeId) {
      ALL_PLACES_SCOPE_ID -> EverythingGlobalScope.getNameText()
      PROJECT_AND_LIBRARIES_SCOPE_ID -> ProjectAndLibrariesScope.getNameText()
      PROJECT_FILES_SCOPE_ID -> ProjectScope.getProjectFilesScopeName()
      PROJECT_PRODUCTION_FILES_SCOPE_ID -> GlobalSearchScopesCore.getProjectProductionFilesScopeName()
      PROJECT_TEST_FILES_SCOPE_ID -> GlobalSearchScopesCore.getProjectTestFilesScopeName()
      SCRATCHES_AND_CONSOLES_SCOPE_ID -> ScratchesNamedScope.scratchesAndConsoles()
      RECENTLY_VIEWED_FILES_SCOPE_ID -> PredefinedSearchScopeProviderImpl.getRecentlyViewedFilesScopeName()
      RECENTLY_CHANGED_FILES_SCOPE_ID -> PredefinedSearchScopeProviderImpl.getRecentlyChangedFilesScopeName()
      OPEN_FILES_SCOPE_ID -> OpenFilesScope.getNameText()
      CURRENT_FILE_SCOPE_ID -> PredefinedSearchScopeProviderImpl.getCurrentFileScopeName()
      else -> scopeId
    }

  @NonNls
  override fun getScopeSerializationId(@Nls presentableScopeName: String): String {
    if (presentableScopeName == EverythingGlobalScope.getNameText()) return ALL_PLACES_SCOPE_ID
    if (presentableScopeName == ProjectAndLibrariesScope.getNameText()) return PROJECT_AND_LIBRARIES_SCOPE_ID
    if (presentableScopeName == ProjectScope.getProjectFilesScopeName()) return PROJECT_FILES_SCOPE_ID
    if (presentableScopeName == GlobalSearchScopesCore.getProjectProductionFilesScopeName()) return PROJECT_PRODUCTION_FILES_SCOPE_ID
    if (presentableScopeName == GlobalSearchScopesCore.getProjectTestFilesScopeName()) return PROJECT_TEST_FILES_SCOPE_ID
    if (presentableScopeName == ScratchesNamedScope.scratchesAndConsoles()) return SCRATCHES_AND_CONSOLES_SCOPE_ID
    if (presentableScopeName == PredefinedSearchScopeProviderImpl.getRecentlyViewedFilesScopeName()) return RECENTLY_VIEWED_FILES_SCOPE_ID
    if (presentableScopeName == PredefinedSearchScopeProviderImpl.getRecentlyChangedFilesScopeName()) return RECENTLY_CHANGED_FILES_SCOPE_ID
    if (presentableScopeName == OpenFilesScope.getNameText()) return OPEN_FILES_SCOPE_ID
    return if (presentableScopeName == PredefinedSearchScopeProviderImpl.getCurrentFileScopeName()) CURRENT_FILE_SCOPE_ID else presentableScopeName
  }
}