// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.search;

import com.intellij.largeFilesEditor.search.searchResultsPanel.RangeSearch;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface RangeSearchCreator {

  @NotNull
  RangeSearch createContent(Project project,
                            VirtualFile virtualFile,
                            @NlsContexts.TabTitle String titleName);
}
