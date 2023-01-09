// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class HighlightInfoB implements HighlightInfo.Builder {
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
  private String inspectionToolId;
  private PsiElement psiElement;
  private int group;
  private final List<FixInfo> fixes = new ArrayList<>();
  private boolean created;
  private PsiReference unresolvedReference;

  HighlightInfoB(@NotNull HighlightInfoType type) {
    this.type = type;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer) {
    assertNotCreated();
    assert this.gutterIconRenderer == null : "gutterIconRenderer already set";
    this.gutterIconRenderer = gutterIconRenderer;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder problemGroup(@NotNull ProblemGroup problemGroup) {
    assertNotCreated();
    assert this.problemGroup == null : "problemGroup already set";
    this.problemGroup = problemGroup;
    return this;
  }

  private void assertNotCreated() {
    assert !created : "Must not call this method after Builder.create() was called";
  }

  @NotNull
  @Override
  public HighlightInfo.Builder inspectionToolId(@NotNull String inspectionToolId) {
    assertNotCreated();
    assert this.inspectionToolId == null : "inspectionToolId already set";
    this.inspectionToolId = inspectionToolId;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder description(@NotNull String description) {
    assertNotCreated();
    assert escapedDescription == null : "description already set";
    escapedDescription = description;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder descriptionAndTooltip(@NotNull String description) {
    return description(description).unescapedToolTip(description);
  }

  @NotNull
  @Override
  public HighlightInfo.Builder textAttributes(@NotNull TextAttributes attributes) {
    assertNotCreated();
    assert forcedTextAttributes == null : "textAttributes already set";
    forcedTextAttributes = attributes;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder textAttributes(@NotNull TextAttributesKey attributesKey) {
    assertNotCreated();
    assert forcedTextAttributesKey == null : "textAttributesKey already set";
    forcedTextAttributesKey = attributesKey;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder unescapedToolTip(@NotNull String unescapedToolTip) {
    assertNotCreated();
    assert escapedToolTip == null : "Tooltip was already set";
    escapedToolTip = htmlEscapeToolTip(unescapedToolTip);
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder escapedToolTip(@NotNull String escapedToolTip) {
    assertNotCreated();
    assert this.escapedToolTip == null : "Tooltip was already set";
    this.escapedToolTip = escapedToolTip;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder range(int start, int end) {
    assertNotCreated();
    assert startOffset == -1 && endOffset == -1 : "Offsets already set";

    startOffset = start;
    endOffset = end;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder range(@NotNull TextRange textRange) {
    assertNotCreated();
    assert startOffset == -1 && endOffset == -1 : "Offsets already set";
    startOffset = textRange.getStartOffset();
    endOffset = textRange.getEndOffset();
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder range(@NotNull ASTNode node) {
    return range(node.getPsi());
  }

  @NotNull
  @Override
  public HighlightInfo.Builder range(@NotNull PsiElement element) {
    assertNotCreated();
    assert psiElement == null : " psiElement already set";
    psiElement = element;
    return range(element.getTextRange());
  }

  @NotNull
  @Override
  public HighlightInfo.Builder range(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
    TextRange absoluteRange = rangeInElement.shiftRight(element.getTextRange().getStartOffset());
    return range(element, absoluteRange.getStartOffset(), absoluteRange.getEndOffset());
  }

  @NotNull
  @Override
  public HighlightInfo.Builder range(@NotNull PsiElement element, int start, int end) {
    assertNotCreated();
    assert psiElement == null : " psiElement already set";
    psiElement = element;
    return range(start, end);
  }

  @NotNull
  @Override
  public HighlightInfo.Builder endOfLine() {
    assertNotCreated();
    isAfterEndOfLine = true;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder needsUpdateOnTyping(boolean update) {
    assertNotCreated();
    assert myNeedsUpdateOnTyping == null : " needsUpdateOnTyping already set";
    myNeedsUpdateOnTyping = update;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder severity(@NotNull HighlightSeverity severity) {
    assertNotCreated();
    assert this.severity == null : " severity already set";
    this.severity = severity;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder fileLevelAnnotation() {
    assertNotCreated();
    isFileLevelAnnotation = true;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder navigationShift(int navigationShift) {
    assertNotCreated();
    this.navigationShift = navigationShift;
    return this;
  }

  @NotNull
  @Override
  public HighlightInfo.Builder group(int group) {
    assertNotCreated();
    this.group = group;
    return this;
  }

  void setUnresolvedReference(@NotNull PsiReference ref) {
    unresolvedReference = ref;
  }

  private record FixInfo(@NotNull IntentionAction action,
                         @Nullable List<? extends IntentionAction> options,
                         @Nls @Nullable String displayName,
                         @Nullable TextRange fixRange,
                         @Nullable HighlightDisplayKey key) {
  }

  @Override
  public HighlightInfo.@NotNull Builder registerFix(@NotNull IntentionAction action,
                                                    @Nullable List<? extends IntentionAction> options,
                                                    @Nls @Nullable String displayName,
                                                    @Nullable TextRange fixRange,
                                                    @Nullable HighlightDisplayKey key) {
    assertNotCreated();
    fixes.add(new FixInfo(action, options, displayName, fixRange, key));
    return this;
  }

  @Nullable
  @Override
  public HighlightInfo create() {
    HighlightInfo info = createUnconditionally();
    LOG.assertTrue(psiElement != null ||
                   severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY ||
                   severity == HighlightInfoType.INJECTED_FRAGMENT_SEVERITY ||
                   ArrayUtil.find(HighlightSeverity.DEFAULT_SEVERITIES, severity) != -1,
                   "Custom type requires not-null element to detect its text attributes");
    return isAcceptedByFilters(info, psiElement) ? info : null;
  }

  @NotNull
  @Override
  public HighlightInfo createUnconditionally() {
    assertNotCreated();
    created = true;
    if (severity == null) {
      severity = type.getSeverity(psiElement);
    }
    HighlightInfo info = new HighlightInfo(forcedTextAttributes, forcedTextAttributesKey, type, startOffset, endOffset, escapedDescription,
                                           escapedToolTip, severity, isAfterEndOfLine, myNeedsUpdateOnTyping, isFileLevelAnnotation,
                                           navigationShift,
                                           problemGroup, inspectionToolId, gutterIconRenderer, group, unresolvedReference);
    for (FixInfo fix : fixes) {
      info.registerFix(fix.action(), fix.options(), fix.displayName(), fix.fixRange(), fix.key());
    }
    return info;
  }

  @Nullable
  private static @NlsContexts.Tooltip String htmlEscapeToolTip(@Nullable @NlsContexts.Tooltip String unescapedTooltip) {
    return unescapedTooltip == null ? null : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(unescapedTooltip));
  }

  private static boolean isAcceptedByFilters(@NotNull HighlightInfo info, @Nullable PsiElement psiElement) {
    PsiFile file = psiElement == null ? null : psiElement.getContainingFile();
    for (HighlightInfoFilter filter : HighlightInfoFilter.EXTENSION_POINT_NAME.getExtensions()) {
      if (!filter.accept(info, file)) {
        return false;
      }
    }
    info.psiElement = psiElement;
    return true;
  }
}
