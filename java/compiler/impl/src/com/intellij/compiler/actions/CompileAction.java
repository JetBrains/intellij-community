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
package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactBySourceFileFinder;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CompileAction extends CompileActionBase {
  protected void doAction(DataContext dataContext, Project project) {
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module != null) {
      CompilerManager.getInstance(project).compile(module, null);
    }
    else {
      VirtualFile[] files = getCompilableFiles(project, CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext));
      if (files.length > 0) {
        CompilerManager.getInstance(project).compile(files, null);
      }
    }

  }

  public void update(AnActionEvent event) {
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    DataContext dataContext = event.getDataContext();

    presentation.setText(ActionsBundle.actionText(IdeActions.ACTION_COMPILE));
    presentation.setVisible(true);

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);

    final VirtualFile[] files = getCompilableFiles(project, CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext));
    if (module == null && files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(!ActionPlaces.isPopupPlace(event.getPlace()));
      return;
    }

    String elementDescription = null;
    if (module != null) {
      elementDescription = CompilerBundle.message("action.compile.description.module", module.getName());
    }
    else {
      PsiPackage aPackage = null;
      if (files.length == 1) {
        final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(files[0]);
        if (directory != null) {
          aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        }
      }
      else {
        PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        if (element instanceof PsiPackage) {
          aPackage = (PsiPackage)element;
        }
      }

      if (aPackage != null) {
        String name = aPackage.getQualifiedName();
        if (name.length() == 0) {
          //noinspection HardCodedStringLiteral
          name = "<default>";
        }
        elementDescription = "'" + name + "'";
      }
      else if (files.length == 1) {
        final VirtualFile file = files[0];
        FileType fileType = file.getFileType();
        if (CompilerManager.getInstance(project).isCompilableFileType(fileType) || isCompilableResourceFile(project, compilerConfiguration, file)) {
          elementDescription = "'" + file.getName() + "'";
        }
        else {
          if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
            // the action should be invisible in popups for non-java files
            presentation.setEnabled(false);
            presentation.setVisible(false);
            return;
          }
        }
      }
      else {
        elementDescription = CompilerBundle.message("action.compile.description.selected.files");
      }
    }

    if (elementDescription == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setText(createPresentationText(elementDescription), true);
    presentation.setEnabled(true);
  }

  private static String createPresentationText(String elementDescription) {
    StringBuffer buffer = new StringBuffer(40);
    buffer.append(ActionsBundle.actionText(IdeActions.ACTION_COMPILE)).append(" ");
    int length = elementDescription.length();
    if (length > 23) {
      if (StringUtil.startsWithChar(elementDescription, '\'')) {
        buffer.append("'");
      }
      buffer.append("...");
      buffer.append(elementDescription.substring(length - 20, length));
    }
    else {
      buffer.append(elementDescription);
    }
    return buffer.toString();
  }

  private static VirtualFile[] getCompilableFiles(Project project, VirtualFile[] files) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final PsiManager psiManager = PsiManager.getInstance(project);
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    final List<VirtualFile> filesToCompile = new ArrayList<VirtualFile>();
    for (final VirtualFile file : files) {
      if (!fileIndex.isInSourceContent(file)) {
        continue;
      }
      if (!file.isInLocalFileSystem()) {
        continue;
      }
      if (file.isDirectory()) {
        final PsiDirectory directory = psiManager.findDirectory(file);
        if (directory == null || JavaDirectoryService.getInstance().getPackage(directory) == null) {
          continue;
        }
      }
      else {
        FileType fileType = file.getFileType();
        if (!(compilerManager.isCompilableFileType(fileType) || isCompilableResourceFile(project, compilerConfiguration, file))) {
          continue;
        }
      }
      filesToCompile.add(file);
    }
    return VfsUtil.toVirtualFileArray(filesToCompile);
  }

  private static boolean isCompilableResourceFile(final Project project, final CompilerConfiguration compilerConfiguration, final VirtualFile file) {
    if (!compilerConfiguration.isResourceFile(file)) {
      return false;
    }
    final Collection<? extends Artifact> artifacts = ArtifactBySourceFileFinder.getInstance(project).findArtifacts(file);
    return artifacts.isEmpty();
  }
}