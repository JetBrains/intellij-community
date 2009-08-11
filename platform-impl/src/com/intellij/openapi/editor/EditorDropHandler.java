package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public interface EditorDropHandler {
  boolean canHandleDrop(DataFlavor[] transferFlavors);
  void handleDrop(Transferable t, final Project project);
}
