// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remote;

import com.intellij.util.AbstractPathMapper;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathMapper;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RemoteProcessUtil {
  @Contract("null -> null")
  public static String toRemoteFileSystemStyle(@Nullable String path) {
    return path == null ? null : RemoteFile.createRemoteFile(path).getPath();
  }

  public static String[] buildRemoteCommand(@NotNull AbstractPathMapper pathMapper, @NotNull Collection<String> commands) {
    return ArrayUtilRt.toStringArray(pathMapper.convertToRemote(commands));
  }

  public static @NotNull String remapPathsList(@NotNull String pathsValue, @NotNull PathMapper pathMapper, @NotNull String interpreterPath) {
    boolean isWin = RemoteFile.isWindowsPath(interpreterPath);
    List<String> mappedPaths = new ArrayList<>();
    for (String path : pathsValue.split(File.pathSeparator)) {
      mappedPaths.add(RemoteFile.createRemoteFile(pathMapper.convertToRemote(path), isWin).getPath());
    }
    return String.join(isWin ? ";" : ":", mappedPaths);
  }
}
