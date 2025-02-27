// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public abstract class HighlightInfoUpdater {
  public static HighlightInfoUpdater getInstance(Project project) {
    return project.getService(HighlightInfoUpdater.class);
  }

  /**
   * Tool {@code toolId} has generated (maybe empty) {@code newInfos} highlights during visiting PsiElement {@code visitedPsiElement}.
   * Remove all highlights that this tool had generated earlier during visiting this psi element, and replace them with {@code newInfosGenerated}
   * Do not read below, it's very private and just for me
   * @param toolId one of
   *               {@code String}: the tool is a {@link LocalInspectionTool} with its {@link LocalInspectionTool#getShortName()}==toolId
   *               {@code Class<? extends Annotator>}: the tool is an {@link com.intellij.lang.annotation.Annotator} of the corresponding class
   *               {@code Class<? extends HighlightVisitor>}: the tool is a {@link HighlightVisitor} of the corresponding class
   *               {@code Object: Injection background and syntax from InjectedGeneralHighlightingPass#INJECTION_BACKGROUND_ID }
   */
  @ApiStatus.Internal
  public abstract void psiElementVisited(@NotNull Object toolId,
                                  @NotNull PsiElement visitedPsiElement,
                                  @NotNull List<? extends HighlightInfo> newInfos,
                                  @NotNull Document hostDocument,
                                  @NotNull PsiFile psiFile,
                                  @NotNull Project project,
                                  @NotNull HighlightingSession session,
                                  @NotNull ManagedHighlighterRecycler invalidElementRecycler);

  @ApiStatus.Internal
  public void removeInfosForInjectedFilesOtherThan(@NotNull PsiFile hostPsiFile,
                                                   @NotNull TextRange restrictRange,
                                                   @NotNull HighlightingSession highlightingSession,
                                                   @NotNull Collection<? extends FileViewProvider> liveInjectedFiles) {}

  /**
   * {@link HighlightInfoUpdater} which doesn't update markup model. Useful for obtaining highlighting without showing anything
    */
  @ApiStatus.Internal
  public static final @NotNull HighlightInfoUpdater EMPTY = new HighlightInfoUpdater(){
    @Override
    public void psiElementVisited(@NotNull Object toolId,
                           @NotNull PsiElement visitedPsiElement,
                           @NotNull List<? extends HighlightInfo> newInfos,
                           @NotNull Document hostDocument,
                           @NotNull PsiFile psiFile,
                           @NotNull Project project,
                           @NotNull HighlightingSession session,
                           @NotNull ManagedHighlighterRecycler invalidElementRecycler) {
    }
  };
}
