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
class HighlightInfoB implements HighlightInfo.Builder {
  private static final Logger LOG = Logger.getInstance(HighlightInfoB.class);
  private Boolean myNeedsUpdateOnTyping;
  private TextAttributes forcedTextAttributes;
  private TextAttributesKey forcedTextAttributesKey;

  private final HighlightInfoType type;
  private final boolean isCopy; // true if this Builder is a copy of existing HighlightInfo with (almost) all fields pre-filled, so no checks "this field already set" are performed
  private int startOffset = UNSET_INT_VALUE;
  private int endOffset = UNSET_INT_VALUE;

  private @NlsContexts.DetailedDescription String escapedDescription;
  private @NlsContexts.Tooltip String escapedToolTip;
  private HighlightSeverity severity;

  private Boolean isAfterEndOfLine;
  private Boolean isFileLevelAnnotation;
  private int navigationShift = UNSET_INT_VALUE;

  private GutterIconRenderer gutterIconRenderer;
  private ProblemGroup problemGroup;
  private PsiElement psiElement;
  private int group = UNSET_INT_VALUE;
  private final List<HighlightInfo.IntentionActionDescriptor> fixes = new ArrayList<>();
  private boolean created;
  private final List<Consumer<? super QuickFixActionRegistrar>> myLazyFixes = new ArrayList<>();
  private static final int UNSET_INT_VALUE = -2039480982;
  HighlightInfoB(@NotNull HighlightInfoType type, boolean isCopy) {
    this.type = type;
    this.isCopy = isCopy;
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
    if (created) {
      throw new IllegalArgumentException("Must not call this method after .create() was called");
    }
  }
  private void assertNotSet(Object field, @NotNull String fieldName) {
    if (!isCopy && field != null) {
      throw new IllegalArgumentException(fieldName +" already set (to "+field+")");
    }
  }
  private void assertNotSet(int field, @NotNull String fieldName) {
    if (!isCopy && field != UNSET_INT_VALUE) {
      throw new IllegalArgumentException(fieldName +" already set (to "+field+")");
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
    return escapedToolTip(htmlEscapeToolTip(unescapedToolTip));
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
    assertNotSet(startOffset, "Start offset already set");
    assertNotSet(endOffset, "End offset already set");
    startOffset = start;
    endOffset = end;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull TextRange textRange) {
    return range(textRange.getStartOffset(), textRange.getEndOffset());
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull ASTNode node) {
    return range(node.getPsi());
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull PsiElement psiElement) {
    TextRange range = psiElement.getTextRange();
    return range(psiElement, range.getStartOffset(), range.getEndOffset());
  }

  @Override
  public @NotNull HighlightInfo.Builder range(@NotNull PsiElement psiElement, @NotNull TextRange rangeInElement) {
    TextRange absoluteRange = rangeInElement.shiftRight(psiElement.getTextRange().getStartOffset());
    return range(psiElement, absoluteRange.getStartOffset(), absoluteRange.getEndOffset());
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
    assertNotSet(this.isAfterEndOfLine, "isAfterEndOfLine");
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
    assertNotSet(this.isFileLevelAnnotation, "isFileLevelAnnotation");
    isFileLevelAnnotation = true;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder navigationShift(int navigationShift) {
    assertNotCreated();
    assertNotSet(this.navigationShift, "navigationShift");
    this.navigationShift = navigationShift;
    return this;
  }

  @Override
  public @NotNull HighlightInfo.Builder group(int group) {
    assertNotCreated();
    assertNotSet(this.group, "group");
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
                                           escapedToolTip, severity, isAfterEndOfLine != null && isAfterEndOfLine,
                                           myNeedsUpdateOnTyping, isFileLevelAnnotation != null && isFileLevelAnnotation,
                                           getValueOrDefault(navigationShift),
                                           problemGroup, null, gutterIconRenderer, getValueOrDefault(group), false, myLazyFixes);
    // fill IntentionActionDescriptor.problemGroup and IntentionActionDescriptor.severity - they can be null because .registerFix() might have been called before .problemGroup() and .severity()
    List<HighlightInfo.IntentionActionDescriptor> iads = ContainerUtil.map(fixes, fixInfo -> fixInfo.withProblemGroupAndSeverity(problemGroup, severity));
    info.registerFixes(iads, null);
    return info;
  }

  private static int getValueOrDefault(int field) {
    return field == UNSET_INT_VALUE ? 0 : field;
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
