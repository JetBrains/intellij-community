package com.intellij.openapi.fileChooser;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Dialog to save a file
 *
 * @author Konstantin Bulenkov
 * @see FileChooserFactory
 * @since 9.0
 */
public interface FileSaverDialog {
  
  @Nullable
  VirtualFileWrapper save(@Nullable VirtualFile baseDir, @Nullable String filename);
}
