/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.util.diff.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.util.diff.impl.DiffRequestFactory;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: migrate old DIFF_REQUEST key ?
public class CompareFilesAction extends BaseShowDiffAction {
  public static final DataKey<DiffRequest> DIFF_REQUEST = DataKey.create("CompareFilesAction.DiffRequest");

  protected boolean isAvailable(@NotNull AnActionEvent e) {
    DiffRequest request = e.getData(DIFF_REQUEST);
    if (request != null) {
      return true;
    }

    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files == null || files.length != 2) {
      return false;
    }

    if (!files[0].isValid() || !files[1].isValid()) {
      return false;
    }

    return true;
  }

  @Nullable
  @Override
  protected DiffRequest getDiffRequest(@NotNull AnActionEvent e) {
    final DiffRequest diffRequest = DIFF_REQUEST.getData(e.getDataContext());
    if (diffRequest != null) {
      return diffRequest;
    }

    final VirtualFile[] data = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (data == null || data.length != 2) {
      return null;
    }

    return DiffRequestFactory.createFromFile(e.getProject(), data[0], data[1]);
  }
}
