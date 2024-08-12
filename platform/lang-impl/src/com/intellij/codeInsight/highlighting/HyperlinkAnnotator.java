// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.PsiReferenceService.Hints;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static java.util.Objects.requireNonNull;

@ApiStatus.NonExtendable
public class HyperlinkAnnotator implements Annotator, DumbAware {

  private static final Key<@Nls String> messageKey = Key.create("hyperlink.message");

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (holder.isBatchMode()) return;

    if (WebReference.isWebReferenceWorthy(element)) {
      // asking for references on every element is too expensive, only ask for it on potential external reference hosts
      // not only slow, but also creates a lot of cached values and SoftReference instances in all elements
      for (var reference : PsiSymbolReferenceService.getService().getReferences(element, PsiHighlightedReference.class)) {
        String message = reference.highlightMessage();
        AnnotationBuilder annotationBuilder = message == null ? holder.newSilentAnnotation(reference.highlightSeverity())
                                                              : holder.newAnnotation(reference.highlightSeverity(), message);
        reference.highlightReference(annotationBuilder.range(reference.getAbsoluteRange())).create();
      }

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

  private static final Key<ParameterizedCachedValue<List<PsiReference>, PsiElement>> REFS_KEY = Key.create("HyperlinkAnnotator");
  private static final ParameterizedCachedValueProvider<List<PsiReference>, PsiElement> REFS_PROVIDER = element -> {
    List<PsiReference> references;
    try {
      references = PsiReferenceService.getService().getReferences(element, Hints.HIGHLIGHTED_REFERENCES);
    }
    catch (IndexNotReadyException ignored) {
      return Result.create(emptyList(), DumbService.getInstance(element.getProject()));
    }

    if (references.isEmpty()) references = emptyList();
    return Result.create(references, PsiModificationTracker.MODIFICATION_COUNT);
  };

  private static @NotNull List<PsiReference> getReferences(@NotNull PsiElement element) {
    return CachedValuesManager.getManager(element.getProject())
      .getParameterizedCachedValue(element, REFS_KEY, REFS_PROVIDER, false, element);
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

  @ApiStatus.Internal
  public static @Nls @NotNull String getMessage() {
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
