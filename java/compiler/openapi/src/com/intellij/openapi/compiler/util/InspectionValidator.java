// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.compiler.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public abstract class InspectionValidator {
  public static final ProjectExtensionPointName<InspectionValidator> EP_NAME = new ProjectExtensionPointName<>("com.intellij.compiler.inspectionValidator");
  private final String myId;
  private final @Nls String myDescription;
  private final @NlsContexts.ProgressText String myProgressIndicatorText;

  private final Class<? extends LocalInspectionTool> @Nullable [] myInspectionToolClasses;

  private final @Nullable InspectionToolProvider myInspectionToolProvider;

  protected InspectionValidator(@NotNull @NonNls String id, @NotNull @Nls String description,
                                @NotNull @Nls String progressIndicatorText) {
    myId = id;
    myDescription = description;
    myProgressIndicatorText = progressIndicatorText;
    myInspectionToolClasses = null;
    myInspectionToolProvider = null;
  }

  public abstract boolean isAvailableOnScope(@NotNull CompileScope scope);

  public abstract Collection<VirtualFile> getFilesToProcess(final Project project, final CompileContext context);

  public @NotNull Collection<? extends PsiElement> getDependencies(final PsiFile psiFile) {
    return Collections.emptyList();
  }

  public Class<? extends LocalInspectionTool> @NotNull [] getInspectionToolClasses(final CompileContext context) {
    if (myInspectionToolClasses != null) {
      return myInspectionToolClasses;
    }

    assert myInspectionToolProvider != null : "getInspectionToolClasses() must be overridden";
    return myInspectionToolProvider.getInspectionClasses();
  }

  public final @NotNull String getId() {
    return myId;
  }

  public final @Nls String getDescription() {
    return myDescription;
  }

  public final @NlsContexts.ProgressText String getProgressIndicatorText() {
    return myProgressIndicatorText;
  }

  public CompilerMessageCategory getCategoryByHighlightDisplayLevel(final @NotNull HighlightDisplayLevel severity,
                                                                    final @NotNull VirtualFile virtualFile,
                                                                    final @NotNull CompileContext context) {
    if (severity == HighlightDisplayLevel.ERROR) return CompilerMessageCategory.ERROR;
    if (severity == HighlightDisplayLevel.WARNING) return CompilerMessageCategory.WARNING;
    return CompilerMessageCategory.INFORMATION;
  }

  public @NotNull Map<ProblemDescriptor, HighlightDisplayLevel> checkAdditionally(PsiFile file) {
    return Collections.emptyMap();
  }
}
