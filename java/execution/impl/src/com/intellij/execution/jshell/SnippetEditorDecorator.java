/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.jshell;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * Date: 06-Jun-17
 */
public class SnippetEditorDecorator extends EditorNotifications.Provider<JComponent>{
  public static final Key<JComponent> CONTEXT_KEY = Key.create("jshell.editor.toolbar");

  @NotNull
  @Override
  public Key<JComponent> getKey() {
    return CONTEXT_KEY;
  }

  @Nullable
  @Override
  public JComponent createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    final RootType root = ScratchFileService.getInstance().getRootType(file);
    
    if ((root instanceof JShellRootType)) {
      final DefaultActionGroup actions = new DefaultActionGroup(ExecuteJShellAction.getSharedInstance(), DropJShellStateAction.getSharedInstance());
      final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("JShellSnippetEditor", actions, true);

      final EditorHeaderComponent header = new EditorHeaderComponent();
      header.add(toolbar.getComponent());
      return header;
    }

    return null;
  }
}
