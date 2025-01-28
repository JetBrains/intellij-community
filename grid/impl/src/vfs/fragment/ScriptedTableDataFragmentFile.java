package com.intellij.database.vfs.fragment;

import com.intellij.database.loaders.DataLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ScriptedTableDataFragmentFile extends TableDataFragmentFile {
  private final DataLoader myLoader;

  public ScriptedTableDataFragmentFile(@NotNull VirtualFile originalFile, @NotNull TextRange range, @NotNull DataLoader loader) {
    super(originalFile, range);
    myLoader = loader;
  }

  public @NotNull DataLoader getLoader() {
    return myLoader;
  }
}
