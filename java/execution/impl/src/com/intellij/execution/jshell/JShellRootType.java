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

import com.intellij.execution.console.ConsoleRootType;
import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: 09-May-17
 */
public final class JShellRootType extends ConsoleRootType {
  public static final String CONTENT_ID = "jshell_console";

  public JShellRootType() {
    super("jshell", "JShell Console");
  }

  @NotNull
  public static JShellRootType getInstance() {
    return findByClass(JShellRootType.class);
  }

  @NotNull
  @Override
  public String getDefaultFileExtension() {
    return JShellFileType.DEFAULT_EXTENSION;
  }

  @NotNull
  @Override
  public String getContentPathName(@NotNull String id) {
    assert id == CONTENT_ID;
    return CONTENT_ID;
  }

  @Override
  public void fileOpened(@NotNull final VirtualFile file, @NotNull FileEditorManager source) {
    for (FileEditor fileEditor : source.getAllEditors(file)) {
      if (fileEditor instanceof TextEditor) {
        ExecuteJShellAction.getSharedInstance().registerCustomShortcutSet(CommonShortcuts.CTRL_ENTER, fileEditor.getComponent());
      }
    }
  }

}
