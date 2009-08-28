package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface HttpVirtualFileListener extends EventListener {

  void fileDownloaded(@NotNull VirtualFile file);

}
