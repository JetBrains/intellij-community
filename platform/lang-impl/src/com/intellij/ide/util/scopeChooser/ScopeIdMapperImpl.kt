// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.ide.scratch.ScratchesNamedScope
import com.intellij.openapi.fileEditor.impl.OpenFilesScope
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.PredefinedSearchScopeProviderImpl
import com.intellij.psi.search.ProjectAndLibrariesScope
import com.intellij.psi.search.ProjectScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ScopeIdMapperImpl : ScopeIdMapper() {
  override fun getPresentableScopeName(scopeId: String): @Nls String = when (scopeId) {
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
    else -> @Suppress("HardCodedStringLiteral") scopeId
  }

  override fun getScopeSerializationId(presentableScopeName: @Nls String): String = when (presentableScopeName) {
    EverythingGlobalScope.getNameText() -> ALL_PLACES_SCOPE_ID
    ProjectAndLibrariesScope.getNameText() -> PROJECT_AND_LIBRARIES_SCOPE_ID
    ProjectScope.getProjectFilesScopeName() -> PROJECT_FILES_SCOPE_ID
    GlobalSearchScopesCore.getProjectProductionFilesScopeName() -> PROJECT_PRODUCTION_FILES_SCOPE_ID
    GlobalSearchScopesCore.getProjectTestFilesScopeName() -> PROJECT_TEST_FILES_SCOPE_ID
    ScratchesNamedScope.scratchesAndConsoles() -> SCRATCHES_AND_CONSOLES_SCOPE_ID
    PredefinedSearchScopeProviderImpl.getRecentlyViewedFilesScopeName() -> RECENTLY_VIEWED_FILES_SCOPE_ID
    PredefinedSearchScopeProviderImpl.getRecentlyChangedFilesScopeName() -> RECENTLY_CHANGED_FILES_SCOPE_ID
    OpenFilesScope.getNameText() -> OPEN_FILES_SCOPE_ID
    PredefinedSearchScopeProviderImpl.getCurrentFileScopeName() -> CURRENT_FILE_SCOPE_ID
    else -> presentableScopeName
  }
}
