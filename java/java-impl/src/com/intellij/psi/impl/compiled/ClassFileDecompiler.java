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

/*
 * @author max
 */
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.ContentBasedFileSubstitutor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.compiled.ClassFileDecompilers;
import org.jetbrains.annotations.NotNull;

public class ClassFileDecompiler implements BinaryFileDecompiler {
  @Override
  @NotNull
  public CharSequence decompile(@NotNull VirtualFile file) {
    ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
    if (decompiler instanceof ClassFileDecompilers.Full) {
      PsiManager manager = PsiManager.getInstance(ProjectManager.getInstance().getDefaultProject());
      return ((ClassFileDecompilers.Full)decompiler).createFileViewProvider(file, manager, true).getContents();
    }

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    if (projects.length > 0) {
      Project project = projects[0];
      //noinspection deprecation
      for (ContentBasedFileSubstitutor processor : Extensions.getExtensions(ContentBasedFileSubstitutor.EP_NAME)) {
        if (processor.isApplicable(project, file)) {
          return processor.obtainFileText(project, file);
        }
      }
    }

    return ClsFileImpl.decompile(file);
  }
}