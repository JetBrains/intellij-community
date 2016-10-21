/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.jrt;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class JrtFileSystem extends ArchiveFileSystem {
  public static final String PROTOCOL = StandardFileSystems.JRT_PROTOCOL;
  public static final String PROTOCOL_PREFIX = StandardFileSystems.JRT_PROTOCOL_PREFIX;
  public static final String SEPARATOR = URLUtil.JAR_SEPARATOR;

  private static final boolean SUPPORTED =
    SystemInfo.isJavaVersionAtLeast("9") || SystemInfo.isJavaVersionAtLeast("1.8") && !SystemInfo.isJavaVersionAtLeast("1.9");

  public static boolean isSupported() {
    return SUPPORTED;
  }

  public static boolean isModularJdk(@NotNull String homePath) {
    return new File(homePath, "lib/modules").exists() && new File(homePath, "jrt-fs.jar").isFile();
  }

  public static boolean isRoot(@NotNull VirtualFile file) {
    return file.getParent() == null && file.getFileSystem() instanceof JrtFileSystem;
  }

  public static boolean isModuleRoot(@NotNull VirtualFile file) {
    VirtualFile parent = file.getParent();
    return parent != null && isRoot(parent);
  }
}