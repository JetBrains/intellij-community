/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaretSpecificDataContext implements DataContext {
  private final DataContext myDelegate;
  private final Caret myCaret;

  public CaretSpecificDataContext(@NotNull DataContext delegate, @NotNull Caret caret) {
    myDelegate = delegate;
    myCaret = caret;
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    Project project = CommonDataKeys.PROJECT.getData(myDelegate);
    if (project == null) {
      return null;
    }
    Object data = FileEditorManager.getInstance(project).getData(dataId, myCaret.getEditor(), myCaret);
    return data == null ? myDelegate.getData(dataId) : data;
  }
}
