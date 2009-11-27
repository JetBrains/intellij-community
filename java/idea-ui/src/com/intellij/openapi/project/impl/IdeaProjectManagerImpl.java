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

import com.intellij.conversion.ConversionResult;
import com.intellij.conversion.ConversionService;
import com.intellij.conversion.impl.ConversionResultImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author mike
 */
public class IdeaProjectManagerImpl extends ProjectManagerImpl {
  public IdeaProjectManagerImpl(VirtualFileManagerEx virtualFileManagerEx) {
    super(virtualFileManagerEx);
  }

  @NotNull
  private static ConversionResult convertProject(final String filePath) throws ProcessCanceledException {
    final String fp = canonicalize(filePath);

    final File f = new File(fp);
    if (fp != null && f.exists() && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return ConversionService.getInstance().convert(fp);
    }
    return ConversionResultImpl.CONVERSION_NOT_NEEDED;
  }

  @Nullable
  protected Project convertAndLoadProject(String filePath, boolean convert) throws IOException {
    final ConversionResult conversionResult;
    if (convert) {
      conversionResult = convertProject(filePath);
      if (conversionResult.openingIsCanceled()) {
        return null;
      }
    }
    else {
      conversionResult = null;
    }

    final Project project = loadProjectWithProgress(filePath);
    if (project == null) return null;

    if (conversionResult != null && !conversionResult.conversionNotNeeded()) {
      StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
        public void run() {
          conversionResult.postStartupActivity(project);
        }
      });
    }
    return project;
  }
}
