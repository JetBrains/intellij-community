// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.script;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 */
public final class IdeConsoleRootType extends com.intellij.execution.console.IdeConsoleRootType  {
  IdeConsoleRootType() {
    super("ide", LangBundle.message("root.type.ide.consoles"));
  }

  public static @NotNull IdeConsoleRootType getInstance() {
    return findByClass(IdeConsoleRootType.class);
  }

  @Override
  public @Nullable Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence());
    Icon icon = fileType == UnknownFileType.INSTANCE || fileType == PlainTextFileType.INSTANCE ?
                AllIcons.Debugger.Console : ObjectUtils.notNull(fileType.getIcon(), AllIcons.Debugger.Console);
    return LayeredIcon.create(icon, AllIcons.Nodes.RunnableMark);
  }

  @Override
  public void fileOpened(@NotNull VirtualFile file, @NotNull FileEditorManager source) {
    RunIdeConsoleAction.configureConsole(file, source);
  }
}
