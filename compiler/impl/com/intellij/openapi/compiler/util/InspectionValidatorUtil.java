/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.compiler.util;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author peter
 */
public class InspectionValidatorUtil {
  private InspectionValidatorUtil() {
  }

  public static void addDescriptor(@NotNull final Collection<VirtualFile> result, @NotNull final CompileScope scope, @Nullable final ConfigFile configFile) {
    if (configFile != null) {
      final VirtualFile virtualFile = configFile.getVirtualFile();
      addVirtualFile(result, scope, virtualFile);
    }
  }

  public static void addVirtualFile(final Collection<VirtualFile> result, final CompileScope scope, final VirtualFile virtualFile) {
    if (virtualFile != null && scope.belongs(virtualFile.getUrl())) {
      result.add(virtualFile);
    }
  }
}
