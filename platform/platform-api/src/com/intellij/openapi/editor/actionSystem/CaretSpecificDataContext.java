/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataContextWrapper;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaretSpecificDataContext extends DataContextWrapper {
  private final Caret myCaret;

  public CaretSpecificDataContext(@NotNull DataContext delegate, @NotNull Caret caret) {
    super(delegate);
    myCaret = caret;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    Project project = (Project)super.getData(CommonDataKeys.PROJECT.getName());
    if (project != null) {
      FileEditorManager fm = FileEditorManager.getInstance(project);
      if (fm != null) {
        Object data = fm.getData(dataId, myCaret.getEditor(), myCaret);
        if (data != null) return data;
      }
    }
    if (CommonDataKeys.CARET.is(dataId)) return myCaret;
    return super.getData(dataId);
  }
}
