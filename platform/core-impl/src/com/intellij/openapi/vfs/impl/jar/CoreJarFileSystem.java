// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public final class CoreJarFileSystem extends DeprecatedVirtualFileSystem {
  private final Map<String, CoreJarHandler> myHandlers =
    ConcurrentFactoryMap.createMap(key -> new CoreJarHandler(CoreJarFileSystem.this, key));

  @NotNull
  @Override
  public String getProtocol() {
    return StandardFileSystems.JAR_PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    Couple<String> pair = splitPath(path);
    return myHandlers.get(pair.first).findFileByPath(pair.second);
  }

  @NotNull
  static Couple<String> splitPath(@NotNull String path) {
    int separator = path.indexOf("!/");
    if (separator < 0) {
      throw new IllegalArgumentException("Path in JarFileSystem must contain a separator: " + path);
    }
    String localPath = path.substring(0, separator);
    String pathInJar = path.substring(separator + 2);
    return Couple.of(localPath, pathInJar);
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
}
