// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.actions;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.task.ProjectTaskManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CompileAction extends CompileActionBase {

  private final boolean myIsForFiles;
  private final String myBundleKey;

  public CompileAction() {
    this(false, IdeActions.ACTION_COMPILE);
  }

  protected CompileAction(boolean forFiles, String key) {
    myIsForFiles = forFiles;
    myBundleKey = key;
  }

  @Override
  protected void doAction(@NotNull AnActionEvent event, Project project) {
    final DataContext dataContext = event.getDataContext();
    Module module = dataContext.getData(LangDataKeys.MODULE_CONTEXT);
    if (module != null) {
      ProjectTaskManager.getInstance(project).rebuild(module);
    }
    else {
      VirtualFile[] files = getCompilableFiles(project, dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
      if (files.length > 0) {
        ProjectTaskManager.getInstance(project).compile(files);
      }
      else {
        module = dataContext.getData(PlatformCoreDataKeys.MODULE); // fallback to any module available from the context
        if (module != null) {
          ProjectTaskManager.getInstance(project).rebuild(module);
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }

    presentation.setText(ActionsBundle.actionText(myBundleKey));
    presentation.setVisible(true);

    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    boolean forFiles = false;
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    final VirtualFile[] files;
    if (module != null) {
      files = VirtualFile.EMPTY_ARRAY;
    }
    else {
      files = getCompilableFiles(project, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
      if (files.length == 0 && !ActionPlaces.isShortcutPlace(e.getPlace())) {
        module = e.getData(PlatformCoreDataKeys.MODULE); // fallback to any module available from the context
      }
    }
    if (module == null && files.length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(!ActionPlaces.isPopupPlace(e.getPlace()));
      return;
    }

    String elementDescription = null;
    if (module != null) {
      elementDescription = JavaCompilerBundle.message("action.compile.description.module", module.getName());
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
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (element instanceof PsiPackage) {
          aPackage = (PsiPackage)element;
        }
      }

      if (aPackage != null) {
        String name = aPackage.getQualifiedName();
        if (name.length() == 0) {
          name = "<default>";
        }
        elementDescription = "'" + name + "'";
      }
      else if (files.length == 1) {
        forFiles = true;
        final VirtualFile file = files[0];
        FileType fileType = file.getFileType();
        if (CompilerManager.getInstance(project).isCompilableFileType(fileType) || CompilerConfiguration.getInstance(project).isCompilableResourceFile(project, file)) {
          elementDescription = "'" + file.getName() + "'";
        }
        else {
          if (!ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
            // the action should be invisible in popups for non-java files
            presentation.setEnabledAndVisible(false);
            return;
          }
        }
      }
      else {
        forFiles = true;
        elementDescription = JavaCompilerBundle.message("action.compile.description.selected.files");
      }
    }

    if (elementDescription == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setText(createPresentationText(elementDescription), true);
    presentation.setEnabledAndVisible(forFiles == myIsForFiles);
  }

  private @NlsSafe String createPresentationText(String elementDescription) {
    StringBuilder buffer = new StringBuilder(40);
    buffer.append(ActionsBundle.actionText(myBundleKey)).append(" ");
    int length = elementDescription.length();
    if (length > 50) {
      if (StringUtil.startsWithChar(elementDescription, '\'')) {
        buffer.append("'");
      }
      buffer.append("...");
      buffer.append(elementDescription, length - 20, length);
    }
    else {
      buffer.append(elementDescription);
    }
    return buffer.toString();
  }

  protected static VirtualFile[] getCompilableFiles(Project project, VirtualFile[] files) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final PsiManager psiManager = PsiManager.getInstance(project);
    final CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(project);
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final CompilerManager compilerManager = CompilerManager.getInstance(project);
    final List<VirtualFile> filesToCompile = new ArrayList<>();
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
        if (!(compilerManager.isCompilableFileType(fileType) || compilerConfiguration.isCompilableResourceFile(project, file))) {
          continue;
        }
      }
      filesToCompile.add(file);
    }
    return VfsUtilCore.toVirtualFileArray(filesToCompile);
  }
}