package com.intellij.openapi.editor;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public interface EditorDropHandler {
  boolean canHandleDrop(DataFlavor[] transferFlavors);
  void handleDrop(Transferable t, Editor e);
}
