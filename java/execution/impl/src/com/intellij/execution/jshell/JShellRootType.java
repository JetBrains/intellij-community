// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell;

import com.intellij.execution.console.ConsoleRootType;
import com.intellij.ide.highlighter.JShellFileType;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellRootType extends ConsoleRootType {
  public static final String CONTENT_ID = "jshell_console";

  public JShellRootType() {
    super("jshell", JavaCompilerBundle.message("jshell.console"));
  }

  public static @NotNull JShellRootType getInstance() {
    return findByClass(JShellRootType.class);
  }

  @Override
  public @NotNull String getDefaultFileExtension() {
    return JShellFileType.DEFAULT_EXTENSION;
  }

  @Override
  public @NotNull String getContentPathName(@NotNull String id) {
    assert id.equals(CONTENT_ID);
    return CONTENT_ID;
  }

  @Override
  public void fileOpened(final @NotNull VirtualFile file, @NotNull FileEditorManager source) {
    for (FileEditor fileEditor : source.getAllEditors(file)) {
      if (fileEditor instanceof TextEditor) {
        ExecuteJShellAction.getSharedInstance().registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), fileEditor.getComponent());
      }
    }
  }

}
