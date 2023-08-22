// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.ContributedReferencesAnnotators;
import com.intellij.lang.annotation.*;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.paths.WebReference;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceService.Hints;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.unmodifiableOrEmptyList;
import static java.util.Objects.requireNonNull;

public final class HyperlinkAnnotator implements Annotator {

  private static final Key<@Nls String> messageKey = Key.create("hyperlink.message");

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (holder.isBatchMode()) return;

    for (PsiHighlightedReference reference : PsiSymbolReferenceService.getService().getReferences(element, PsiHighlightedReference.class)) {
      TextRange range = reference.getAbsoluteRange();
      String message = reference.highlightMessage();
      AnnotationBuilder annotationBuilder = message == null ? holder.newSilentAnnotation(reference.highlightSeverity())
                                                            : holder.newAnnotation(reference.highlightSeverity(), message);
      reference.highlightReference(annotationBuilder.range(range)).create();
    }

    if (WebReference.isWebReferenceWorthy(element)) {
      annotateContributedReferences(element, holder);
    }
  }

  private static void annotateContributedReferences(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    List<PsiReference> references = getReferences(element);

    if (!annotateHyperlinks(element, holder, references)) {
      return; // all references highlighted already
    }

    List<ContributedReferencesAnnotator> annotators =
      ContributedReferencesAnnotators.INSTANCE.allForLanguageOrAny(holder.getCurrentAnnotationSession().getFile().getLanguage());

    for (ContributedReferencesAnnotator annotator : annotators) {
      annotator.annotate(element, references, holder);
    }
  }

  @NotNull
  private static List<PsiReference> getReferences(@NotNull PsiElement element) {
    return CachedValuesManager.getCachedValue(element, () -> {
      List<PsiReference> references = PsiReferenceService.getService().getReferences(element, Hints.HIGHLIGHTED_REFERENCES);
      return Result.create(unmodifiableOrEmptyList(references), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static boolean annotateHyperlinks(@NotNull PsiElement element,
                                            @NotNull AnnotationHolder holder,
                                            @NotNull List<PsiReference> references) {
    boolean hasUnprocessedReferences = false;

    for (PsiReference reference : references) {
      if (reference instanceof WebReference) {
        String message = holder.getCurrentAnnotationSession().getUserData(messageKey);
        if (message == null) {
          message = getMessage();
          holder.getCurrentAnnotationSession().putUserData(messageKey, message);
        }
        TextRange range = reference.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
        holder.newAnnotation(HighlightSeverity.INFORMATION, message)
          .range(range)
          .textAttributes(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES)
          .create();
      }
      else if (reference instanceof HighlightedReference) {
        if (reference.isSoft() && !((HighlightedReference)reference).isHighlightedWhenSoft()) continue;

        TextRange rangeInElement = reference.getRangeInElement();
        if (rangeInElement.isEmpty()) continue;

        TextRange range = rangeInElement.shiftRight(element.getTextRange().getStartOffset());
        holder.newSilentAnnotation(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY)
          .range(range)
          .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
          .create();
      }
      else {
        hasUnprocessedReferences = true;
      }
    }
    return hasUnprocessedReferences;
  }

  @Nls
  @NotNull
  @ApiStatus.Internal
  public static String getMessage() {
    String message = IdeBundle.message("open.url.in.browser.tooltip");
    Shortcut[] shortcuts = requireNonNull(KeymapManager.getInstance()).getActiveKeymap().getShortcuts(IdeActions.ACTION_GOTO_DECLARATION);
    String shortcutText = "";
    Shortcut mouseShortcut = ContainerUtil.find(shortcuts, shortcut -> !shortcut.isKeyboard());
    if (mouseShortcut != null) {
      shortcutText += KeymapUtil.getShortcutText(mouseShortcut);
      shortcutText = shortcutText.replace("Button1 ", "");
    }
    Shortcut keyboardShortcut = ContainerUtil.find(shortcuts, shortcut -> shortcut.isKeyboard());
    if (keyboardShortcut != null) {
      if (!shortcutText.isEmpty()) shortcutText += ", ";
      shortcutText += KeymapUtil.getShortcutText(keyboardShortcut);
    }
    if (!shortcutText.isEmpty()) {
      message += " (" + shortcutText + ")";
    }
    return message;
  }
}
