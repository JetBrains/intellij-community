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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class JarPathUtil {
  public static final String JAR_SEPARATOR = "!/";

  @NotNull
  public static File getLocalFile(@NotNull String fullPath) {
    final int i = fullPath.indexOf(JAR_SEPARATOR);
    String filePath = i == -1 ? fullPath : fullPath.substring(0, i);
    return new File(FileUtil.toSystemDependentName(filePath));
  }
}
