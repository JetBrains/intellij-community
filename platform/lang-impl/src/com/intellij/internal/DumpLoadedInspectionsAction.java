// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

final class DumpLoadedInspectionsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    var profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    var allTools = profile.getAllEnabledInspectionTools(project);
    var loadedTools = ContainerUtil.filter(allTools, t -> t.getTool().isInitialized());

    String text = loadedTools.stream()
      .map(t -> t.getTool().getLanguage() + " " + t.getTool().getShortName())
      .collect(Collectors.joining("\n"));

    LightVirtualFile file = new LightVirtualFile("Inspections.txt", PlainTextFileType.INSTANCE,
                                                 "All count: " + allTools.size() + "\n" +
                                                 "Loaded count: " + loadedTools.size() + "\n\n" + text);

    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    if (!fileEditorManager.isFileOpen(file)) {
      fileEditorManager.openEditor(new OpenFileDescriptor(project, file), true);
    }
  }
}
