// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
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

final class ChameleonSyntaxHighlightingPass extends GeneralHighlightingPass {
  static final class Factory implements MainHighlightingPassFactory, TextEditorHighlightingPassFactoryRegistrar {
    @Override
    public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
      registrar.registerTextEditorHighlightingPass(this, null, new int[]{Pass.UPDATE_ALL}, false, -1);
    }

    @Override
    public @NotNull TextEditorHighlightingPass createHighlightingPass(@NotNull PsiFile file, @NotNull Editor editor) {
      Project project = file.getProject();
      TextRange restrict = FileStatusMap.getDirtyTextRange(editor.getDocument(), file, Pass.UPDATE_ALL);
      if (restrict == null) return new ProgressableTextEditorHighlightingPass.EmptyPass(project, editor.getDocument());
      ProperTextRange priority = HighlightingSessionImpl.getFromCurrentIndicator(file).getVisibleRange();
      return new ChameleonSyntaxHighlightingPass(file, editor.getDocument(), ProperTextRange.create(restrict), priority, editor);
    }

    @Override
    public @NotNull TextEditorHighlightingPass createMainHighlightingPass(@NotNull PsiFile file,
                                                                          @NotNull Document document,
                                                                          @NotNull HighlightInfoProcessor highlightInfoProcessor) {
      ProperTextRange range = ProperTextRange.from(0, document.getTextLength());
      return new ChameleonSyntaxHighlightingPass(file, document, range, range, null);
    }
  }

  private ChameleonSyntaxHighlightingPass(@NotNull PsiFile file,
                                          @NotNull Document document,
                                          @NotNull ProperTextRange restrictRange,
                                          @NotNull ProperTextRange priorityRange,
                                          @Nullable Editor editor) {
    super(file, document, restrictRange.getStartOffset(), restrictRange.getEndOffset(), true, priorityRange, editor,
          true, true, true, HighlightInfoUpdater.getInstance(file.getProject()));
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
    HighlightInfoHolder holderInside = new HighlightInfoHolder(myFile);
    HighlightInfoHolder holderOutside = new HighlightInfoHolder(myFile);
    for (PsiElement e : lazyInside) {
      collectHighlights(e, holderInside, holderOutside, myPriorityRange);
    }
    List<HighlightInfo> inside = new ArrayList<>(100);
    List<HighlightInfo> outside = new ArrayList<>(100);
    for (int i=0; i<holderInside.size();i++) {
      inside.add(holderInside.get(i));
    }
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(), myProject, true);
    BackgroundUpdateHighlightersUtil.setHighlightersInRange(myPriorityRange, inside, markupModel, getId(), getHighlightingSession());
    for (PsiElement e : lazyOutside) {
      collectHighlights(e, holderInside, holderOutside, myPriorityRange);
    }
    for (int i=0; i<holderOutside.size();i++) {
      outside.add(holderOutside.get(i));
    }
    BackgroundUpdateHighlightersUtil.setHighlightersOutsideRange(outside, myRestrictRange, myPriorityRange, getId(), getHighlightingSession());
    myHighlights.addAll(inside);
    myHighlights.addAll(outside);
    setProgressLimit(1);
    advanceProgress(1);
  }

  private void collectHighlights(@NotNull PsiElement element,
                                 @NotNull HighlightInfoHolder inside,
                                 @NotNull HighlightInfoHolder outside,
                                 @NotNull ProperTextRange priorityRange) {
    EditorColorsScheme scheme = ObjectUtils.notNull(getColorsScheme(), EditorColorsManager.getInstance().getGlobalScheme());

    Language language = ILazyParseableElementType.LANGUAGE_KEY.get(element.getNode());
    if (language == null) return;

    SyntaxHighlighter syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, myProject, myFile.getVirtualFile());
    for (PsiElement token : SyntaxTraverser.psiTraverser(element).traverse(TreeTraversal.LEAVES_DFS)) {
      TextRange tokenRange = token.getTextRange();
      if (tokenRange.isEmpty()) continue;
      IElementType type = PsiUtilCore.getElementType(token);
      @NotNull HighlightInfoHolder holder = priorityRange.contains(tokenRange) ? inside : outside;
      TextAttributesKey[] keys = syntaxHighlighter.getTokenHighlights(type);
      List<HighlightInfo> infos = InjectedLanguageFragmentSyntaxUtil.addSyntaxInjectedFragmentInfo(scheme, tokenRange, keys, null);
      for (HighlightInfo info : infos) {
        holder.add(info);
      }
    }
  }

  @Override
  protected void applyInformationWithProgress() {
  }

  @Override
  protected @Nullable String getPresentableName() {
    return null; // do not show progress for
  }
}
