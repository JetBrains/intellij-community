// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser;

import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.openapi.fileEditor.impl.OpenFilesScope;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.intellij.psi.search.ProjectScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.search.GlobalSearchScopesCore.getProjectProductionFilesScopeName;
import static com.intellij.psi.search.GlobalSearchScopesCore.getProjectTestFilesScopeName;
import static com.intellij.psi.search.PredefinedSearchScopeProviderImpl.*;

/**
 * Historically IDE used scope presentable name for serialization (like "Project Files").
 * This approach doesn't work anymore because scope presentable names are different in different locales (different translation bundles).
 * This class helps to map scope serialization IDs to presentable scope name and vice versa.
 * For compatibility, scope serialization IDs are hardcoded in this class and they are equal to scope presentable names in English locale.
 */
public class ScopePresentableNameToSerializationIdMapper {
  // Scope serialization ids are equal to English scope translations (for compatibility with 2020.2-)
  public static final @NonNls String ALL_PLACES_SCOPE_ID = "All Places";
  public static final @NonNls String PROJECT_AND_LIBRARIES_SCOPE_ID = "Project and Libraries";
  public static final @NonNls String PROJECT_FILES_SCOPE_ID = "Project Files";
  public static final @NonNls String PROJECT_PRODUCTION_FILES_SCOPE_ID = "Project Production Files";
  public static final @NonNls String PROJECT_TEST_FILES_SCOPE_ID = "Project Test Files";
  public static final @NonNls String SCRATCHES_AND_CONSOLES_SCOPE_ID = "Scratches and Consoles";
  public static final @NonNls String RECENTLY_VIEWED_FILES_SCOPE_ID = "Recently Viewed Files";
  public static final @NonNls String RECENTLY_CHANGED_FILES_SCOPE_ID = "Recently Changed Files";
  public static final @NonNls String OPEN_FILES_SCOPE_ID = "Open Files";
  public static final @NonNls String CURRENT_FILE_SCOPE_ID = "Current File";

  public static @Nls @NotNull String getPresentableScopeName(@NonNls @NotNull String scopeId) {
    if (scopeId.equals(ALL_PLACES_SCOPE_ID)) return EverythingGlobalScope.getNameText();
    if (scopeId.equals(PROJECT_AND_LIBRARIES_SCOPE_ID)) return ProjectAndLibrariesScope.getNameText();
    if (scopeId.equals(PROJECT_FILES_SCOPE_ID)) return ProjectScope.getProjectFilesScopeName();
    if (scopeId.equals(PROJECT_PRODUCTION_FILES_SCOPE_ID)) return getProjectProductionFilesScopeName();
    if (scopeId.equals(PROJECT_TEST_FILES_SCOPE_ID)) return getProjectTestFilesScopeName();
    if (scopeId.equals(SCRATCHES_AND_CONSOLES_SCOPE_ID)) return ScratchesNamedScope.scratchesAndConsoles();
    if (scopeId.equals(RECENTLY_VIEWED_FILES_SCOPE_ID)) return getRecentlyViewedFilesScopeName();
    if (scopeId.equals(RECENTLY_CHANGED_FILES_SCOPE_ID)) return getRecentlyChangedFilesScopeName();
    if (scopeId.equals(OPEN_FILES_SCOPE_ID)) return OpenFilesScope.getNameText();
    if (scopeId.equals(CURRENT_FILE_SCOPE_ID)) return getCurrentFileScopeName();

    //noinspection HardCodedStringLiteral
    return scopeId;
  }

  public static @NonNls @NotNull String getScopeSerializationId(@Nls @NotNull String presentableScopeName) {
    if (presentableScopeName.equals(EverythingGlobalScope.getNameText())) return ALL_PLACES_SCOPE_ID;
    if (presentableScopeName.equals(ProjectAndLibrariesScope.getNameText())) return PROJECT_AND_LIBRARIES_SCOPE_ID;
    if (presentableScopeName.equals(ProjectScope.getProjectFilesScopeName())) return PROJECT_FILES_SCOPE_ID;
    if (presentableScopeName.equals(getProjectProductionFilesScopeName())) return PROJECT_PRODUCTION_FILES_SCOPE_ID;
    if (presentableScopeName.equals(getProjectTestFilesScopeName())) return PROJECT_TEST_FILES_SCOPE_ID;
    if (presentableScopeName.equals(ScratchesNamedScope.scratchesAndConsoles())) return SCRATCHES_AND_CONSOLES_SCOPE_ID;
    if (presentableScopeName.equals(getRecentlyViewedFilesScopeName())) return RECENTLY_VIEWED_FILES_SCOPE_ID;
    if (presentableScopeName.equals(getRecentlyChangedFilesScopeName())) return RECENTLY_CHANGED_FILES_SCOPE_ID;
    if (presentableScopeName.equals(OpenFilesScope.getNameText())) return OPEN_FILES_SCOPE_ID;
    if (presentableScopeName.equals(getCurrentFileScopeName())) return CURRENT_FILE_SCOPE_ID;

    return presentableScopeName;
  }
}
