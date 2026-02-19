// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;

public final class CoreJarFileSystem extends DeprecatedVirtualFileSystem {
  private final Map<String, CoreJarHandler> myHandlers = ConcurrentFactoryMap.createMap(key -> new CoreJarHandler(this, key));

  @Override
  public @NotNull String getProtocol() {
    return StandardFileSystems.JAR_PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    Pair<String, String> pair = splitPath(path);
    return myHandlers.get(pair.first).findFileByPath(pair.second);
  }

  static @NotNull Pair<String, String> splitPath(@NotNull String path) {
    int separator = path.indexOf(URLUtil.JAR_SEPARATOR);
    if (separator < 0) { 
      throw new IllegalArgumentException("Path in JarFileSystem must contain a separator: " + path);
    }
    return pair(path.substring(0, separator), path.substring(separator + URLUtil.JAR_SEPARATOR.length()));
  }

  @Override
  public void refresh(boolean asynchronous) { }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  public void clearHandlersCache() {
    myHandlers.forEach((path, handler) -> handler.clearCaches());
    myHandlers.clear();
  }

  @ApiStatus.Internal
  public void clearHandler(@NotNull String jarPath) {
    CoreJarHandler handler = myHandlers.remove(jarPath);
    if (handler != null) {
      handler.clearCaches();
    }
  }
}
