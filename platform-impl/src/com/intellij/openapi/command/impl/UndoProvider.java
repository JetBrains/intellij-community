package com.intellij.openapi.command.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

public interface UndoProvider {
  ExtensionPointName<UndoProvider> EP_NAME = ExtensionPointName.create("com.intellij.undoProvider");
  ExtensionPointName<UndoProvider> PROJECT_EP_NAME = ExtensionPointName.create("com.intellij.projectUndoProvider");

  void commandStarted(Project project);
  void commandFinished(Project project);
}