/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.ContentBasedFileSubstitutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/** @deprecated temporary solution, to remove in IDEA 14 */
@SuppressWarnings("deprecation")
public class DeprecatedClassFileDecompiler implements ClassFileDecompiler.PlatformDecompiler {
  @Override
  public CharSequence decompile(@NotNull VirtualFile file) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length > 0) {
      Project project = projects[0];
      for (ContentBasedFileSubstitutor processor : Extensions.getExtensions(ContentBasedFileSubstitutor.EP_NAME)) {
        if (processor.isApplicable(project, file)) {
          return processor.obtainFileText(project, file);
        }
      }
    }

    return null;
  }
}