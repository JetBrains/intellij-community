/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author yole
 */
public class GlobalSearchScopes extends GlobalSearchScopesCore {

  public static final String OPEN_FILES_SCOPE_NAME = "Open Files";

  private GlobalSearchScopes() {
  }

  @NotNull
  public static GlobalSearchScope openFilesScope(@NotNull Project project) {
    final VirtualFile[] files = FileEditorManager.getInstance(project).getOpenFiles();
    if (ArrayUtil.isEmpty(files)) {
      // prefer a scope with meaningful getDisplayName(), avoid GlobalSearchScope.EMPTY_SCOPE
      return GlobalSearchScope.fileScope(project, null, OPEN_FILES_SCOPE_NAME);
    }
    return GlobalSearchScope.filesScope(project, Arrays.asList(files), OPEN_FILES_SCOPE_NAME);
  }
}
