// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public class ExternalSystemTaskLocation extends PsiLocation<PsiElement> {

  private final @NotNull ExternalTaskExecutionInfo myTaskInfo;

  public ExternalSystemTaskLocation(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull ExternalTaskExecutionInfo taskInfo) {
    super(project, psiElement);
    myTaskInfo = taskInfo;
  }

  public @NotNull ExternalTaskExecutionInfo getTaskInfo() {
    return myTaskInfo;
  }

  public static ExternalSystemTaskLocation create(@NotNull Project project,
                                                  @NotNull ProjectSystemId systemId,
                                                  @Nullable String projectPath,
                                                  @NotNull ExternalTaskExecutionInfo taskInfo) {
    if (projectPath != null) {
      final VirtualFile file = VfsUtil.findFileByIoFile(new File(projectPath), false);
      if (file != null) {
        final PsiDirectory psiFile = PsiManager.getInstance(project).findDirectory(file);
        if (psiFile != null) {
          return new ExternalSystemTaskLocation(project, psiFile, taskInfo);
        }
      }
    }

    String name = systemId.getReadableName() + projectPath + StringUtil.join(taskInfo.getSettings().getTaskNames(), " ");
    // We create a dummy text file instead of re-using external system file in order to avoid clashing with other configuration producers.
    // For example gradle files are enhanced groovy scripts but we don't want to run them via regular IJ groovy script runners.
    // Gradle tooling api should be used for running gradle tasks instead. IJ execution sub-system operates on Location objects
    // which encapsulate PsiElement and groovy runners are automatically applied if that PsiElement IS-A GroovyFile.
    PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(name, PlainTextFileType.INSTANCE, "");
    return new ExternalSystemTaskLocation(project, psiFile, taskInfo);
  }
}
