package com.intellij.openapi.fileChooser;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface FileTextField {

  JTextField getField();
  @Nullable
  VirtualFile getSelectedFile();

}
