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
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ErrorTreeView extends Disposable {
  DataKey<Object> CURRENT_EXCEPTION_DATA_KEY = DataKey.create("CURRENT_EXCEPTION_DATA");

  /**
   * If file is not null, allows to navigate to this file, line, column
   */
  void addMessage(int type,
                  @NotNull String[] text,
                  @Nullable VirtualFile file,
                  int line,
                  int column,
                  @Nullable Object data);

  /**
   * Allows adding messages related to other files under 'underFileGroup'
   */
  void addMessage(int type,
                  @NotNull String[] text,
                  @Nullable VirtualFile underFileGroup,
                  @Nullable VirtualFile file,
                  int line,
                  int column,
                  @Nullable Object data);

  /**
   * add message, allowing navigation via custom Navigatable object
   */
  void addMessage(int type,
                  @NotNull String[] text,
                  @Nullable String groupName,
                  @NotNull Navigatable navigatable,
                  @Nullable String exportTextPrefix,
                  @Nullable String rendererTextPrefix,
                  @Nullable Object data);

  @NotNull
  JComponent getComponent();
}
