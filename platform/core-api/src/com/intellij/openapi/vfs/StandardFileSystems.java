/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class StandardFileSystems {
  public static String FILE_PROTOCOL = "file";
  public static String JAR_PROTOCOL = "jar";

  private static final NotNullLazyValue<VirtualFileSystem> ourLocal = new NotNullLazyValue<VirtualFileSystem>() {
    @NotNull
    @Override
    protected VirtualFileSystem compute() {
      return VirtualFileManager.getInstance().getFileSystem(FILE_PROTOCOL);
    }
  };

  private static final NotNullLazyValue<VirtualFileSystem> ourJar = new NotNullLazyValue<VirtualFileSystem>() {
    @NotNull
    @Override
    protected VirtualFileSystem compute() {
      return VirtualFileManager.getInstance().getFileSystem(JAR_PROTOCOL);
    }
  };

  public static VirtualFileSystem local() {
    return ourLocal.getValue();
  }

  public static VirtualFileSystem jar() {
    return ourJar.getValue();
  }
}
