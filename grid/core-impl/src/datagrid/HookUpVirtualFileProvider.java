package com.intellij.database.datagrid;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public interface HookUpVirtualFileProvider {
  @Nullable VirtualFile getVirtualFile();
}
