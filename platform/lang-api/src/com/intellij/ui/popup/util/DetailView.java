/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.popup.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 5/9/12
 * Time: 5:34 AM
 * To change this template use File | Settings | File Templates.
 */
public interface DetailView {
  void updateWithItem(ItemWrapper wrapper);

  Editor getEditor();

  void navigateInPreviewEditor(VirtualFile file, LogicalPosition positionToNavigate);

  JPanel getDetailPanel();

  void setDetailPanel(@Nullable JPanel panel);

  void clearEditor();
}
