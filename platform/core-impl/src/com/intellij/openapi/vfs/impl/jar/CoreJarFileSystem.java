/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author yole
 */
public class CoreJarFileSystem extends DeprecatedVirtualFileSystem {
  private final Map<String, CoreJarHandler> myHandlers = new ConcurrentFactoryMap<String, CoreJarHandler>() {
    @Nullable
    @Override
    protected CoreJarHandler create(String key) {
      return new CoreJarHandler(CoreJarFileSystem.this, key);
    }
  };

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
  protected Couple<String> splitPath(@NotNull String path) {
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

  @SuppressWarnings("unused")  // used in Kotlin
  public void clearHandlersCache() {
    myHandlers.clear();
  }
}
