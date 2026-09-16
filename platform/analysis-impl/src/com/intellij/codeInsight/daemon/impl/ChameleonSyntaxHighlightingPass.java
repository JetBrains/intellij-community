// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeHighlighting.MainHighlightingPassFactory;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.TreeTraversal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ChameleonSyntaxHighlightingPass extends ProgressableTextEditorHighlightingPass {
  static final Object CHAMELEON_SYNTAX_TOOL_ID = ObjectUtils.sentinel("CHAMELEON_SYNTAX_TOOL_ID");

  private final @NotNull ProperTextRange myPriorityRange;
  private final @NotNull HighlightInfoUpdater myHighlightInfoUpdater;
  private volatile List<HighlightInfo> myHighlights = List.of();

  static final class Factory implements MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
    @Override
    public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
      registrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL}, false, -1);
    }

    @Override
    public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile psiFile, @NotNull Editor editor) {
      Project project = psiFile.getProject();
      TextRange restrict = FileStatusMap.getDirtyTextRange(editor.getDocument(), psiFile, Pass.UPDATE_ALL);
      if (restrict == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(project, editor.getDocument());
      ProperTextRange priority = DaemonCodeAnalyzerEx.getInstanceEx(project).getHighlightSessionFromCurrentIndicator(psiFile).getVisibleRange();
      return new ChameleonSyntaxHighlightingPass(psiFile, editor.getDocument(), ProperTextRange.create(restrict), priority, editor,
                                                  HighlightInfoUpdater.getInstance(project));
    }

    @Override
    public @NotNull TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile psiFile,
                                                                          @NotNull Document document,
                                                                          @NotNull HighlightInfoProcessor highlightInfoProcessor) {
      ProperTextRange range = ProperTextRange.from(0, document.getTextLength());
      return new ChameleonSyntaxHighlightingPass(psiFile, document, range, range, null, HighlightInfoUpdater.EMPTY);
    }
  }

  private ChameleonSyntaxHighlightingPass(@NotNull PsiFile psiFile,
                                          @NotNull Document document,
                                          @NotNull ProperTextRange restrictRange,
                                          @NotNull ProperTextRange priorityRange,
                                          @Nullable Editor editor,
                                          @NotNull HighlightInfoUpdater highlightInfoUpdater) {
    super(psiFile.getProject(), document, AnalysisBundle.message("pass.chameleon"), psiFile, editor, restrictRange, false, HighlightInfoProcessor.getEmpty());
    myPriorityRange = priorityRange;
    myHighlightInfoUpdater = highlightInfoUpdater;
  }

  @Override
  public @NotNull List<HighlightInfo> getInfos() {
    return myHighlights;
  }

  @Override
  public void collectInformationWithProgress(@NotNull ProgressIndicator progress) {
    SyntaxTraverser<PsiElement> s = SyntaxTraverser.psiTraverser(myFile)
                                                   .filter(o -> {
        IElementType type = PsiUtilCore.getElementType(o);
        return type instanceof ILazyParseableElementType && !(type instanceof IFileElementType);
      });

    List<PsiElement> lazyOutside = new ArrayList<>(100);
    List<PsiElement> lazyInside = new ArrayList<>(100);

    for (PsiElement e : s) {
      (e.getTextRange().intersects(myPriorityRange) ? lazyInside : lazyOutside).add(e);
    }
    List<HighlightInfo> highlights = new ArrayList<>();
    Consumer<ManagedHighlighterRecycler> recyclerConsumer = invalidPsiRecycler -> {
      visitElements(lazyInside, highlights, invalidPsiRecycler);
      visitElements(lazyOutside, highlights, invalidPsiRecycler);
    };
    if (myHighlightInfoUpdater instanceof HighlightInfoUpdaterImpl impl) {
      impl.runWithInvalidPsiRecycler(getHighlightingSession(), HighlightInfoUpdaterImpl.WhatTool.CHAMELEON_SYNTAX, recyclerConsumer);
    }
    else {
      ManagedHighlighterRecycler.runWithRecycler(getHighlightingSession(), "ChameleonSyntaxHighlightingPass", recyclerConsumer);
    }
    myHighlights = List.copyOf(highlights);
    setProgressLimit(1);
    advanceProgress(1);
  }

  private void visitElements(@NotNull List<? extends PsiElement> elements,
                             @NotNull List<? super HighlightInfo> allHighlights,
                             @NotNull ManagedHighlighterRecycler invalidPsiRecycler) {
    for (PsiElement element : elements) {
      List<HighlightInfo> highlights = collectHighlights(element);
      myHighlightInfoUpdater.psiElementVisited(CHAMELEON_SYNTAX_TOOL_ID, element, highlights, getDocument(), myFile, myProject,
                                                getHighlightingSession(), invalidPsiRecycler);
      allHighlights.addAll(highlights);
    }
  }

  private @NotNull List<HighlightInfo> collectHighlights(@NotNull PsiElement element) {
    EditorColorsScheme scheme = ObjectUtils.notNull(getColorsScheme(), EditorColorsManager.getInstance().getGlobalScheme());

    Language language = ILazyParseableElementType.LANGUAGE_KEY.get(element.getNode());
    if (language == null) return List.of();

    HighlightInfoHolder holder = new HighlightInfoHolder(myFile);
    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, myProject, myFile.getVirtualFile());
    for (PsiElement token : SyntaxTraverser.psiTraverser(element).traverse(TreeTraversal.LEAVES_DFS)) {
      TextRange tokenRange = token.getTextRange();
      if (tokenRange.isEmpty()) continue;
      IElementType type = PsiUtilCore.getElementType(token);
      TextAttributesKey[] keys = type==null ? TextAttributesKey.EMPTY_ARRAY : syntaxHighlighter.getTokenHighlights(type);
      List<HighlightInfo> infos =
        InjectedLanguageFragmentSyntaxUtil.addSyntaxInjectedFragmentInfo(scheme, tokenRange, keys, CHAMELEON_SYNTAX_TOOL_ID);
      for (HighlightInfo info : infos) {
        holder.add(info);
      }
    }
    List<HighlightInfo> highlights = new ArrayList<>(holder.size());
    for (int i = 0; i < holder.size(); i++) {
      highlights.add(holder.get(i));
    }
    return highlights;
  }

  @Override
  protected void applyInformationWithProgress() {
  }

  @Override
  public @Nullable String getPresentableName() {
    return null; // do not show progress for
  }
}
