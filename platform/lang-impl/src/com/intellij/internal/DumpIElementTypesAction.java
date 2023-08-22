// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.stream.Collectors;

final class DumpIElementTypesAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    IElementType[] allTypes = IElementType.enumerate(IElementType.TRUE);

    String text = Arrays.stream(allTypes)
      .map(t ->  t.getLanguage().getID() + " " + t.getClass() + " " + t.getDebugName())
      .collect(Collectors.joining("\n"));

    LightVirtualFile file = new LightVirtualFile("IElementType.txt", PlainTextFileType.INSTANCE,
                                                 "COUNT: " + allTypes.length + "\n" + text);

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (!fileEditorManager.isFileOpen(file)) {
      fileEditorManager.openEditor(new OpenFileDescriptor(project, file), true);
    }
  }
}
