// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class HighlightInfoB implements HighlightInfo.Builder {
  private static final Logger LOG = Logger.getInstance(HighlightInfoB.class);
  private Boolean myNeedsUpdateOnTyping;
  private TextAttributes forcedTextAttributes;
  private TextAttributesKey forcedTextAttributesKey;

  private final HighlightInfoType type;
  private int startOffset = -1;
  private int endOffset = -1;

  private @NlsContexts.DetailedDescription String escapedDescription;
  private @NlsContexts.Tooltip String escapedToolTip;
  private HighlightSeverity severity;

  private boolean isAfterEndOfLine;
  private boolean isFileLevelAnnotation;
  private int navigationShift;

  private GutterIconRenderer gutterIconRenderer;
  private ProblemGroup problemGroup;
  private PsiElement psiElement;
  private int group;
  private final List<HighlightInfo.IntentionActionDescriptor> fixes = new ArrayList<>();
  private boolean created;
  private final List<Consumer<? super QuickFixActionRegistrar>> myLazyFixes = new ArrayList<>();

  HighlightInfoB(@NotNull HighlightInfoType type) {
    this.type = type;
  }

  @Override
  public @NotNull HighlightInfo.Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer) {
    assertNotCreated();
    assertNotSet(this.gutterIconRenderer, "gutterIconRenderer");
    this.gutterIconRenderer = gutterIconRenderer;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder problemGroup(@NotNull ProblemGroup problemGroup) {
    assertNotCreated();
    assertNotSet(this.problemGroup, "problemGroup");
    this.problemGroup = problemGroup;
    return this;
  }

  private void assertNotCreated() {
    assert !created : "Must not call this method after Builder.create() was called";
  }
  private static void assertNotSet(Object field, @NotNull String fieldName) {
    if (field != null) {
      throw new IllegalArgumentException(fieldName +" already set");
    }
  }


  @Override
  public @NotNull HighlightInfo.Builder inspectionToolId(@NotNull String inspectionToolId) {
    assertNotCreated();
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder description(@NotNull String description) {
    assertNotCreated();
    assertNotSet(this.escapedDescription, "description");
    escapedDescription = description;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder descriptionAndTooltip(@NotNull String description) {
    return description(description).unescapedToolTip(description);
  }

  @Override
  public @NotNull HighlightInfo.Builder textAttributes(@NotNull TextAttributes attributes) {
    assertNotCreated();
    assertNotSet(this.forcedTextAttributes, "textAttributes");
    forcedTextAttributes = attributes;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder textAttributes(@NotNull TextAttributesKey attributesKey) {
    assertNotCreated();
    assertNotSet(this.forcedTextAttributesKey, "textAttributes");
    forcedTextAttributesKey = attributesKey;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder unescapedToolTip(@NotNull String unescapedToolTip) {
    assertNotCreated();
    assertNotSet(this.escapedToolTip, "tooltip");
    escapedToolTip = htmlEscapeToolTip(unescapedToolTip);
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder escapedToolTip(@NotNull String escapedToolTip) {
    assertNotCreated();
    assertNotSet(this.escapedToolTip, "tooltip");
    this.escapedToolTip = escapedToolTip;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder range(int start, int end) {
    assertNotCreated();
    assert startOffset == -1 && endOffset == -1 : "Offsets already set";

    startOffset = start;
    endOffset = end;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull TextRange textRange) {
    assertNotCreated();
    assert startOffset == -1 && endOffset == -1 : "Offsets already set";
    startOffset = textRange.getStartOffset();
    endOffset = textRange.getEndOffset();
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull ASTNode node) {
    return range(node.getPsi());
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull PsiElement element) {
    assertNotCreated();
    assertNotSet(this.psiElement, "psiElement");
    psiElement = element;
    return range(element.getTextRange());
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
    TextRange absoluteRange = rangeInElement.shiftRight(element.getTextRange().getStartOffset());
    return range(element, absoluteRange.getStartOffset(), absoluteRange.getEndOffset());
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull PsiElement element, int start, int end) {
    assertNotCreated();
    assertNotSet(this.psiElement, "psiElement");
    psiElement = element;
    return range(start, end);
  }

  @Override
  public @NotNull HighlightInfo.Builder endOfLine() {
    assertNotCreated();
    isAfterEndOfLine = true;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder needsUpdateOnTyping(boolean update) {
    assertNotCreated();
    assertNotSet(this.myNeedsUpdateOnTyping, "needsUpdateOnTyping");
    myNeedsUpdateOnTyping = update;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder severity(@NotNull HighlightSeverity severity) {
    assertNotCreated();
    assertNotSet(this.severity, "severity");
    this.severity = severity;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder fileLevelAnnotation() {
    assertNotCreated();
    isFileLevelAnnotation = true;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder navigationShift(int navigationShift) {
    assertNotCreated();
    this.navigationShift = navigationShift;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder group(int group) {
    assertNotCreated();
    this.group = group;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder registerFix(@NotNull IntentionAction action,
                                                    @Nullable List<? extends IntentionAction> options,
                                                    @Nls @Nullable String displayName,
                                                    @Nullable TextRange fixRange,
                                                    @Nullable HighlightDisplayKey key) {
    assertNotCreated();
    // both problemGroup and severity are null here since they might haven't been set yet; we'll pass actual values later, in createUnconditionally()
    fixes.add(new HighlightInfo.IntentionActionDescriptor(action, options, displayName, null, key, null, null, fixRange));
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder registerLazyFixes(@NotNull Consumer<? super QuickFixActionRegistrar> quickFixComputer) {
    assertNotCreated();
    myLazyFixes.add(quickFixComputer);
    return this;
  }

  @Override
  public @Nullable HighlightInfo create() {
    HighlightInfo info = createUnconditionally();
    boolean canDeduceTextAttributes = psiElement != null ||
                    severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY ||
                    severity == HighlightInfoType.INJECTED_FRAGMENT_SEVERITY ||
                    ArrayUtil.find(HighlightSeverity.DEFAULT_SEVERITIES, severity) != -1;
    if (!canDeduceTextAttributes) {
      LOG.error("Custom severity(" + severity+") requires passing not-null PSI element to detect its text attributes. " +
                "Please see HighlightInfo.Builder.range(PsiElement) and similar methods.");
    }
    return isAcceptedByFilters(info, psiElement) ? info : null;
  }

  @Override
  public @NotNull HighlightInfo createUnconditionally() {
    assertNotCreated();
    created = true;
    if (severity == null) {
      severity = type.getSeverity(psiElement);
    }
    //noinspection deprecation
    HighlightInfo info = new HighlightInfo(forcedTextAttributes, forcedTextAttributesKey, type, startOffset, endOffset, escapedDescription,
                                           escapedToolTip, severity, isAfterEndOfLine, myNeedsUpdateOnTyping, isFileLevelAnnotation,
                                           navigationShift,
                                           problemGroup, null, gutterIconRenderer, group, false, myLazyFixes);
    // fill IntentionActionDescriptor.problemGroup and IntentionActionDescriptor.severity - they can be null because .registerFix() might have been called before .problemGroup() and .severity()
    List<HighlightInfo.IntentionActionDescriptor> iads = ContainerUtil.map(fixes, fixInfo -> fixInfo.withProblemGroupAndSeverity(problemGroup, severity));
    info.registerFixes(iads, null);
    return info;
  }

  private static @Nullable @NlsContexts.Tooltip String htmlEscapeToolTip(@Nullable @NlsContexts.Tooltip String unescapedTooltip) {
    return unescapedTooltip == null ? null : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(unescapedTooltip));
  }

  static boolean isAcceptedByFilters(@NotNull HighlightInfo info, @Nullable PsiElement psiElement) {
    PsiFile psiFile = psiElement == null ? null : psiElement.getContainingFile();
    for (HighlightInfoFilter filter : HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensionList()) {
      if (!filter.accept(info, psiFile)) {
        return false;
      }
    }
    return true;
  }
  @NotNull
  UnfairTextRange getRangeSoFar() {
    return new UnfairTextRange(startOffset, endOffset);
  }
}
