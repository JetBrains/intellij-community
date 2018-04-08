// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.jetbrains.jsonSchema.JsonSchemaConfigurable.isHttpPath;

public class JsonFileResolver {
  @Nullable
  public static VirtualFile urlToFile(@NotNull String urlString) {
    return VirtualFileManager.getInstance().findFileByUrl(urlString);
  }

  @Nullable
  public static VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile, @Nullable String schemaUrl) {
    if (schemaUrl == null) return null;

    if (StringUtil.startsWithChar(schemaUrl, '.') || !isHttpPath(schemaUrl)) {
      // relative path
      VirtualFile parent = currentFile == null ? null : currentFile.getParent();
      schemaUrl = parent == null ? null : VfsUtilCore.pathToUrl(parent.getPath() + File.separator + schemaUrl);
    }

    if (schemaUrl != null) {
      VirtualFile virtualFile = urlToFile(schemaUrl);
      if (virtualFile != null) return virtualFile;
    }

    return null;
  }

  public static void startFetchingHttpFileIfNeeded(@Nullable VirtualFile path) {
    if (path instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)path).getFileInfo();
      if (info == null || info.getState() == RemoteFileState.DOWNLOADING_NOT_STARTED) {
        path.refresh(true, false);
      }
    }
  }
}
