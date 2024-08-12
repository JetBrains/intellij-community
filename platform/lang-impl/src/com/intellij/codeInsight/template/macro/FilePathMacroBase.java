// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.ide.actions.CopyReferenceUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FilePathMacroBase extends Macro {

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    Project project = context.getProject();
    Editor editor = context.getEditor();
    if (editor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          return calculateResult(virtualFile, project);
        }
      }
    }
    return null;
  }

  protected @Nullable TextResult calculateResult(@NotNull VirtualFile virtualFile, @NotNull Project project) {
    return new TextResult(virtualFile.getName());
  }

  public static final class FileNameWithoutExtensionMacro extends FilePathMacroBase {

    @Override
    public String getName() {
      return "fileNameWithoutExtension";
    }

    @Override
    protected TextResult calculateResult(@NotNull VirtualFile virtualFile, @NotNull Project project) {
      return new TextResult(virtualFile.getNameWithoutExtension());
    }
  }

  public static final class FileNameMacro extends FilePathMacroBase {
    @Override
    public String getName() {
      return "fileName";
    }

    @Override
    protected TextResult calculateResult(@NotNull VirtualFile virtualFile, @NotNull Project project) {
      return new TextResult(virtualFile.getName());
    }
  }

  public static final class FilePathMacro extends FilePathMacroBase {
    @Override
    public String getName() {
      return "filePath";
    }

    @Override
    protected TextResult calculateResult(@NotNull VirtualFile virtualFile, @NotNull Project project) {
      return new TextResult(FileUtil.toSystemDependentName(virtualFile.getPath()));
    }
  }

  public static final class FileRelativePathMacro extends FilePathMacroBase {
    @Override
    public String getName() {
      return "fileRelativePath";
    }

    @Override
    protected TextResult calculateResult(@NotNull VirtualFile virtualFile, @NotNull Project project) {
      return new TextResult(FileUtil.toSystemDependentName(CopyReferenceUtil.getVirtualFileFqn(virtualFile, project)));
    }
  }
}
