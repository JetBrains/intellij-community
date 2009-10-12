/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.conversion.ConversionService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author mike
 */
public class IdeaProjectManagerImpl extends ProjectManagerImpl {
  public IdeaProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx) {
    super(virtualFileManagerEx);
  }

  @Nullable
  protected Pair<Class, Object> convertProject(final String filePath) throws ProcessCanceledException {
    final String fp = canonicalize(filePath);

    final File f = new File(fp);
    if (fp != null && f.exists() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      final boolean converted = ConversionService.getInstance().convert(fp);
      if (!converted) {
        throw new ProcessCanceledException();
      }
    }
    return null;
  }
}
