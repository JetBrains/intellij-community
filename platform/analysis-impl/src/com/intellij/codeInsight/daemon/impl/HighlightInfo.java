// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.NlsContexts.DetailedDescription;
import static com.intellij.openapi.util.NlsContexts.Tooltip;

public class HighlightInfo implements Segment {
  private static final Logger LOG = Logger.getInstance(HighlightInfo.class);

  // optimization: if tooltip contains this marker object, then it replaced with description field in getTooltip()
  private static final String DESCRIPTION_PLACEHOLDER = "\u0000";

  private static final byte BIJECTIVE_MASK = 0x1;
  private static final byte HAS_HINT_MASK = 0x2;
  private static final byte FROM_INJECTION_MASK = 0x4;
  private static final byte AFTER_END_OF_LINE_MASK = 0x8;
  private static final byte FILE_LEVEL_ANNOTATION_MASK = 0x10;
  private static final byte NEEDS_UPDATE_ON_TYPING_MASK = 0x20;

  @MagicConstant(intValues = {
    BIJECTIVE_MASK, HAS_HINT_MASK, FROM_INJECTION_MASK, AFTER_END_OF_LINE_MASK, FILE_LEVEL_ANNOTATION_MASK, NEEDS_UPDATE_ON_TYPING_MASK})
  private @interface FlagConstant { }

  public final TextAttributes forcedTextAttributes;
  public final TextAttributesKey forcedTextAttributesKey;
  @NotNull
  public final HighlightInfoType type;
  public final int startOffset;
  public final int endOffset;

  public List<Pair<IntentionActionDescriptor, TextRange>> quickFixActionRanges;
  public List<Pair<IntentionActionDescriptor, RangeMarker>> quickFixActionMarkers;
 
  private final String description;
  private final String toolTip;
  @NotNull
  private final HighlightSeverity severity;
  private final GutterMark gutterIconRenderer;
  private final ProblemGroup myProblemGroup;
  private final String inspectionToolId;

  private int group;
  private int fixStartOffset;
  private int fixEndOffset;
  /**
   * @see FlagConstant for allowed values
   */
  private volatile byte myFlags;

  final int navigationShift;
  JComponent fileLevelComponent;
  @Nullable("null means it the same as highlighter") RangeMarker fixMarker;
  volatile RangeHighlighterEx highlighter; // modified in EDT only
  PsiElement psiElement;

  protected HighlightInfo(@Nullable TextAttributes forcedTextAttributes,
                          @Nullable TextAttributesKey forcedTextAttributesKey,
                          @NotNull HighlightInfoType type,
                          int startOffset,
                          int endOffset,
                          @Nullable String escapedDescription,
                          @Nullable String escapedToolTip,
                          @NotNull HighlightSeverity severity,
                          boolean afterEndOfLine,
                          @Nullable Boolean needsUpdateOnTyping,
                          boolean isFileLevelAnnotation,
                          int navigationShift,
                          ProblemGroup problemGroup,
                          @Nullable String inspectionToolId,
                          GutterMark gutterIconRenderer,
                          int group) {
    if (startOffset < 0 || startOffset > endOffset) {
      LOG.error("Incorrect highlightInfo bounds. description="+escapedDescription+"; startOffset="+startOffset+"; endOffset="+endOffset+";type="+type);
    }
    this.forcedTextAttributes = forcedTextAttributes;
    this.forcedTextAttributesKey = forcedTextAttributesKey;
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    fixStartOffset = startOffset;
    fixEndOffset = endOffset;
    description = escapedDescription;
    // optimization: do not retain extra memory if can recompute
    toolTip = encodeTooltip(escapedToolTip, escapedDescription);
    this.severity = severity;
    setFlag(AFTER_END_OF_LINE_MASK, afterEndOfLine);
    setFlag(NEEDS_UPDATE_ON_TYPING_MASK, calcNeedUpdateOnTyping(needsUpdateOnTyping, type));
    setFlag(FILE_LEVEL_ANNOTATION_MASK, isFileLevelAnnotation);
    this.navigationShift = navigationShift;
    myProblemGroup = problemGroup;
    this.gutterIconRenderer = gutterIconRenderer;
    this.inspectionToolId = inspectionToolId;
    this.group = group;
  }

  /**
   * Returns the HighlightInfo instance from which the given range highlighter was created, or null if there isn't any.
   */
  @Nullable
  public static HighlightInfo fromRangeHighlighter(@NotNull RangeHighlighter highlighter) {
    Object errorStripeTooltip = highlighter.getErrorStripeTooltip();
    return errorStripeTooltip instanceof HighlightInfo ? (HighlightInfo)errorStripeTooltip : null;
  }

  @NotNull
  ProperTextRange getFixTextRange() {
    return new ProperTextRange(fixStartOffset, fixEndOffset);
  }

  void setFromInjection(boolean fromInjection) {
    setFlag(FROM_INJECTION_MASK, fromInjection);
  }

  @Nullable
  public String getToolTip() {
    String toolTip = this.toolTip;
    String description = this.description;
    if (toolTip == null || description == null || !toolTip.contains(DESCRIPTION_PLACEHOLDER)) return toolTip;
    String decoded = StringUtil.replace(toolTip, DESCRIPTION_PLACEHOLDER, XmlStringUtil.escapeString(description));
    return XmlStringUtil.wrapInHtml(decoded);
  }

  /**
   * Encodes \p tooltip so that substrings equal to a \p description
   * are replaced with the special placeholder to reduce size of the
   * tooltip. If encoding takes place, <html></html> tags are
   * stripped of the tooltip.
   *
   * @param tooltip - html text
   * @param description - plain text (not escaped)
   *
   * @return encoded tooltip (stripped html text with one or more placeholder characters)
   *         or tooltip without changes.
   */
  private static String encodeTooltip(String tooltip, String description) {
    if (tooltip == null || description == null || description.isEmpty()) return tooltip;

    String encoded = StringUtil.replace(tooltip, XmlStringUtil.escapeString(description), DESCRIPTION_PLACEHOLDER);
    //noinspection StringEquality
    if (encoded == tooltip) {
      return tooltip;
    }
    if (encoded.equals(DESCRIPTION_PLACEHOLDER)) encoded = DESCRIPTION_PLACEHOLDER;
    return XmlStringUtil.stripHtml(encoded);
  }

  public String getDescription() {
    return description;
  }

  @Nullable
  @NonNls
  public String getInspectionToolId() {
    return inspectionToolId;
  }

  private boolean isFlagSet(@FlagConstant byte mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  private void setFlag(@FlagConstant byte mask, boolean value) {
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  boolean isFileLevelAnnotation() {
    return isFlagSet(FILE_LEVEL_ANNOTATION_MASK);
  }

  boolean isBijective() {
    return isFlagSet(BIJECTIVE_MASK);
  }

  void setBijective(boolean bijective) {
    setFlag(BIJECTIVE_MASK, bijective);
  }

  @NotNull
  public HighlightSeverity getSeverity() {
    return severity;
  }

  public RangeHighlighterEx getHighlighter() {
    return highlighter;
  }

  /**
   * Modified only in EDT.
   */
  public void setHighlighter(@Nullable RangeHighlighterEx highlighter) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    this.highlighter = highlighter;
  }

  public boolean isAfterEndOfLine() {
    return isFlagSet(AFTER_END_OF_LINE_MASK);
  }

  @Nullable
  public TextAttributes getTextAttributes(@Nullable PsiElement element, @Nullable EditorColorsScheme editorColorsScheme) {
    if (forcedTextAttributes != null) {
      return forcedTextAttributes;
    }

    EditorColorsScheme colorsScheme = getColorsScheme(editorColorsScheme);

    if (forcedTextAttributesKey != null) {
      return colorsScheme.getAttributes(forcedTextAttributesKey);
    }

    return getAttributesByType(element, type, colorsScheme);
  }

  public static TextAttributes getAttributesByType(@Nullable PsiElement element,
                                                   @NotNull HighlightInfoType type,
                                                   @NotNull TextAttributesScheme colorsScheme) {
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(element != null ? element.getProject() : null);
    TextAttributes textAttributes = severityRegistrar.getTextAttributesBySeverity(type.getSeverity(element));
    if (textAttributes != null) return textAttributes;
    TextAttributesKey key = type.getAttributesKey();
    return colorsScheme.getAttributes(key);
  }

  @Nullable
  @SuppressWarnings("deprecation")
  Color getErrorStripeMarkColor(@NotNull PsiElement element, @Nullable("when null, a global scheme will be used") EditorColorsScheme colorsScheme) {
    if (forcedTextAttributes != null) {
      return forcedTextAttributes.getErrorStripeColor();
    }

    EditorColorsScheme scheme = getColorsScheme(colorsScheme);
    if (forcedTextAttributesKey != null) {
      TextAttributes forcedTextAttributes = scheme.getAttributes(forcedTextAttributesKey);
      if (forcedTextAttributes != null) {
        Color errorStripeColor = forcedTextAttributes.getErrorStripeColor();
        // let's copy above behaviour of forcedTextAttributes stripe color, but I'm not sure the behaviour is correct in general
        if (errorStripeColor != null) {
          return errorStripeColor;
        }
      }
    }

    if (getSeverity() == HighlightSeverity.ERROR) {
      return scheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.WARNING) {
      return scheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.INFO){
      return scheme.getAttributes(CodeInsightColors.INFO_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.WEAK_WARNING){
      return scheme.getAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING) {
      return scheme.getAttributes(CodeInsightColors.GENERIC_SERVER_ERROR_OR_WARNING).getErrorStripeColor();
    }

    TextAttributes attributes = getAttributesByType(element, type, scheme);
    return attributes == null ? null : attributes.getErrorStripeColor();
  }

  @NotNull
  private static EditorColorsScheme getColorsScheme(@Nullable EditorColorsScheme customScheme) {
    return customScheme != null ? customScheme : EditorColorsManager.getInstance().getGlobalScheme();
  }

  @Nullable
  private static String htmlEscapeToolTip(@Nullable String unescapedTooltip) {
    return unescapedTooltip == null ? null : XmlStringUtil.wrapInHtml(XmlStringUtil.escapeString(unescapedTooltip));
  }

  boolean needUpdateOnTyping() {
    return isFlagSet(NEEDS_UPDATE_ON_TYPING_MASK);
  }

  private static boolean calcNeedUpdateOnTyping(@Nullable Boolean needsUpdateOnTyping, HighlightInfoType type) {
    if (needsUpdateOnTyping != null) {
      return needsUpdateOnTyping.booleanValue();
    }
    if (type instanceof HighlightInfoType.UpdateOnTypingSuppressible) {
      return ((HighlightInfoType.UpdateOnTypingSuppressible)type).needsUpdateOnTyping();
    }
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof HighlightInfo)) return false;
    HighlightInfo info = (HighlightInfo)obj;

    return info.getSeverity() == getSeverity() &&
           info.startOffset == startOffset &&
           info.endOffset == endOffset &&
           Comparing.equal(info.type, type) &&
           Comparing.equal(info.gutterIconRenderer, gutterIconRenderer) &&
           Comparing.equal(info.forcedTextAttributes, forcedTextAttributes) &&
           Comparing.equal(info.forcedTextAttributesKey, forcedTextAttributesKey) &&
           Comparing.strEqual(info.getDescription(), getDescription());
  }

  protected boolean equalsByActualOffset(@NotNull HighlightInfo info) {
    if (info == this) return true;

    return info.getSeverity() == getSeverity() &&
           info.getActualStartOffset() == getActualStartOffset() &&
           info.getActualEndOffset() == getActualEndOffset() &&
           Comparing.equal(info.type, type) &&
           Comparing.equal(info.gutterIconRenderer, gutterIconRenderer) &&
           Comparing.equal(info.forcedTextAttributes, forcedTextAttributes) &&
           Comparing.equal(info.forcedTextAttributesKey, forcedTextAttributesKey) &&
           Comparing.strEqual(info.getDescription(), getDescription());
  }

  @Override
  public int hashCode() {
    return startOffset;
  }

  @Override
  public String toString() {
    String s = "HighlightInfo(" + startOffset + "," + endOffset+")";
    if (getActualStartOffset() != startOffset || getActualEndOffset() != endOffset) {
      s += "; actual: (" + getActualStartOffset() + "," + getActualEndOffset() + ")";
    }
    if (highlighter != null) s += " text='" + getText() + "'";
    if (getDescription() != null) s+= ", description='" + getDescription() + "'";
    s += " severity=" + getSeverity();
    s += " group=" + getGroup();
    if (quickFixActionRanges != null) {
      s+= "; quickFixes: "+quickFixActionRanges;
    }
    if (gutterIconRenderer != null) {
      s += "; gutter: " + gutterIconRenderer;
    }
    return s;
  }

  @NotNull
  public static Builder newHighlightInfo(@NotNull HighlightInfoType type) {
    return new B(type);
  }

  void setGroup(int group) {
    this.group = group;
  }

  public interface Builder {
    // only one 'range' call allowed
    @NotNull Builder range(@NotNull TextRange textRange);
    @NotNull Builder range(@NotNull ASTNode node);
    @NotNull Builder range(@NotNull PsiElement element);
    @NotNull Builder range(@NotNull PsiElement element, @NotNull TextRange rangeInElement);
    @NotNull Builder range(@NotNull PsiElement element, int start, int end);
    @NotNull Builder range(int start, int end);

    @NotNull Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer);
    @NotNull Builder problemGroup(@NotNull ProblemGroup problemGroup);
    @NotNull Builder inspectionToolId(@NotNull String inspectionTool);

    // only one allowed
    @NotNull Builder description(@DetailedDescription @NotNull String description);
    @NotNull Builder descriptionAndTooltip(@DetailedDescription @NotNull String description);

    // only one allowed
    @NotNull Builder textAttributes(@NotNull TextAttributes attributes);
    @NotNull Builder textAttributes(@NotNull TextAttributesKey attributesKey);

    // only one allowed
    @NotNull Builder unescapedToolTip(@Tooltip @NotNull String unescapedToolTip);
    @NotNull Builder escapedToolTip(@Tooltip @NotNull String escapedToolTip);

    @NotNull Builder endOfLine();
    @NotNull Builder needsUpdateOnTyping(boolean update);
    @NotNull Builder severity(@NotNull HighlightSeverity severity);
    @NotNull Builder fileLevelAnnotation();
    @NotNull Builder navigationShift(int navigationShift);
    @NotNull Builder group(int group);

    @Nullable("null means filtered out")
    HighlightInfo create();

    @NotNull
    HighlightInfo createUnconditionally();
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

  private static class B implements Builder {
    private Boolean myNeedsUpdateOnTyping;
    private TextAttributes forcedTextAttributes;
    private TextAttributesKey forcedTextAttributesKey;

    private final HighlightInfoType type;
    private int startOffset = -1;
    private int endOffset = -1;

    private String escapedDescription;
    private String escapedToolTip;
    private HighlightSeverity severity;

    private boolean isAfterEndOfLine;
    private boolean isFileLevelAnnotation;
    private int navigationShift;

    private GutterIconRenderer gutterIconRenderer;
    private ProblemGroup problemGroup;
    private String inspectionToolId;
    private PsiElement psiElement;
    private int group;

    private B(@NotNull HighlightInfoType type) {
      this.type = type;
    }

    @NotNull
    @Override
    public Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer) {
      assert this.gutterIconRenderer == null : "gutterIconRenderer already set";
      this.gutterIconRenderer = gutterIconRenderer;
      return this;
    }

    @NotNull
    @Override
    public Builder problemGroup(@NotNull ProblemGroup problemGroup) {
      assert this.problemGroup == null : "problemGroup already set";
      this.problemGroup = problemGroup;
      return this;
    }

    @NotNull
    @Override
    public Builder inspectionToolId(@NotNull String inspectionToolId) {
      assert this.inspectionToolId == null : "inspectionToolId already set";
      this.inspectionToolId = inspectionToolId;
      return this;
    }

    @NotNull
    @Override
    public Builder description(@NotNull String description) {
      assert escapedDescription == null : "description already set";
      escapedDescription = description;
      return this;
    }

    @NotNull
    @Override
    public Builder descriptionAndTooltip(@NotNull String description) {
      return description(description).unescapedToolTip(description);
    }

    @NotNull
    @Override
    public Builder textAttributes(@NotNull TextAttributes attributes) {
      assert forcedTextAttributes == null : "textAttributes already set";
      forcedTextAttributes = attributes;
      return this;
    }

    @NotNull
    @Override
    public Builder textAttributes(@NotNull TextAttributesKey attributesKey) {
      assert forcedTextAttributesKey == null : "textAttributesKey already set";
      forcedTextAttributesKey = attributesKey;
      return this;
    }

    @NotNull
    @Override
    public Builder unescapedToolTip(@NotNull String unescapedToolTip) {
      assert escapedToolTip == null : "Tooltip was already set";
      escapedToolTip = htmlEscapeToolTip(unescapedToolTip);
      return this;
    }

    @NotNull
    @Override
    public Builder escapedToolTip(@NotNull String escapedToolTip) {
      assert this.escapedToolTip == null : "Tooltip was already set";
      this.escapedToolTip = escapedToolTip;
      return this;
    }

    @NotNull
    @Override
    public Builder range(int start, int end) {
      assert startOffset == -1 && endOffset == -1 : "Offsets already set";

      startOffset = start;
      endOffset = end;
      return this;
    }

    @NotNull
    @Override
    public Builder range(@NotNull TextRange textRange) {
      assert startOffset == -1 && endOffset == -1 : "Offsets already set";
      startOffset = textRange.getStartOffset();
      endOffset = textRange.getEndOffset();
      return this;
    }

    @NotNull
    @Override
    public Builder range(@NotNull ASTNode node) {
      return range(node.getPsi());
    }

    @NotNull
    @Override
    public Builder range(@NotNull PsiElement element) {
      assert psiElement == null : " psiElement already set";
      psiElement = element;
      return range(element.getTextRange());
    }

    @NotNull
    @Override
    public Builder range(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
      TextRange absoluteRange = rangeInElement.shiftRight(element.getTextRange().getStartOffset());
      return range(element, absoluteRange.getStartOffset(), absoluteRange.getEndOffset());
    }

    @NotNull
    @Override
    public Builder range(@NotNull PsiElement element, int start, int end) {
      assert psiElement == null : " psiElement already set";
      psiElement = element;
      return range(start, end);
    }

    @NotNull
    @Override
    public Builder endOfLine() {
      isAfterEndOfLine = true;
      return this;
    }

    @NotNull
    @Override
    public Builder needsUpdateOnTyping(boolean update) {
      assert myNeedsUpdateOnTyping == null : " needsUpdateOnTyping already set";
      myNeedsUpdateOnTyping = update;
      return this;
    }

    @NotNull
    @Override
    public Builder severity(@NotNull HighlightSeverity severity) {
      assert this.severity == null : " severity already set";
      this.severity = severity;
      return this;
    }

    @NotNull
    @Override
    public Builder fileLevelAnnotation() {
      isFileLevelAnnotation = true;
      return this;
    }

    @NotNull
    @Override
    public Builder navigationShift(int navigationShift) {
      this.navigationShift = navigationShift;
      return this;
    }

    @NotNull
    @Override
    public Builder group(int group) {
      this.group = group;
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

      if (!isAcceptedByFilters(info, psiElement)) return null;

      return info;
    }

    @NotNull
    @Override
    public HighlightInfo createUnconditionally() {
      if (severity == null) {
        severity = type.getSeverity(psiElement);
      }

      return new HighlightInfo(forcedTextAttributes, forcedTextAttributesKey, type, startOffset, endOffset, escapedDescription,
                               escapedToolTip, severity, isAfterEndOfLine, myNeedsUpdateOnTyping, isFileLevelAnnotation, navigationShift,
                               problemGroup, inspectionToolId, gutterIconRenderer, group);
    }
  }

  public GutterMark getGutterIconRenderer() {
    return gutterIconRenderer;
  }

  @Nullable
  public ProblemGroup getProblemGroup() {
    return myProblemGroup;
  }

  @NotNull
  public static HighlightInfo fromAnnotation(@NotNull Annotation annotation) {
    return fromAnnotation(annotation, false);
  }

  @NotNull
  static HighlightInfo fromAnnotation(@NotNull Annotation annotation, boolean batchMode) {
    TextAttributes forcedAttributes = annotation.getEnforcedTextAttributes();
    TextAttributesKey key = annotation.getTextAttributes();
    TextAttributesKey forcedAttributesKey = forcedAttributes == null && key != HighlighterColors.NO_HIGHLIGHTING ? key : null;

    HighlightInfo info = new HighlightInfo(
      forcedAttributes, forcedAttributesKey, convertType(annotation), annotation.getStartOffset(), annotation.getEndOffset(),
      annotation.getMessage(), annotation.getTooltip(), annotation.getSeverity(), annotation.isAfterEndOfLine(), annotation.needsUpdateOnTyping(),
      annotation.isFileLevelAnnotation(), 0, annotation.getProblemGroup(), null, annotation.getGutterIconRenderer(), Pass.UPDATE_ALL);

    List<? extends Annotation.QuickFixInfo> fixes = batchMode ? annotation.getBatchFixes() : annotation.getQuickFixes();
    if (fixes != null) {
      for (Annotation.QuickFixInfo quickFixInfo : fixes) {
        TextRange range = quickFixInfo.textRange;
        HighlightDisplayKey k = quickFixInfo.key != null ? quickFixInfo.key : HighlightDisplayKey.find(ANNOTATOR_INSPECTION_SHORT_NAME);
        info.registerFix(quickFixInfo.quickFix, null, HighlightDisplayKey.getDisplayNameByKey(k), range, k);
      }
    }

    return info;
  }

  private static final String ANNOTATOR_INSPECTION_SHORT_NAME = "Annotator";

  @NotNull
  private static HighlightInfoType convertType(@NotNull Annotation annotation) {
    ProblemHighlightType type = annotation.getHighlightType();
    HighlightSeverity severity = annotation.getSeverity();
    return toHighlightInfoType(type, severity);
  }

  @NotNull
  private static HighlightInfoType toHighlightInfoType(ProblemHighlightType problemHighlightType, @NotNull HighlightSeverity severity) {
    if (problemHighlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL;
    if (problemHighlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF;
    if (problemHighlightType == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED;
    if (problemHighlightType == ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL) return HighlightInfoType.MARKED_FOR_REMOVAL;
    return convertSeverity(severity);
  }

  @NotNull
  public static HighlightInfoType convertSeverity(@NotNull HighlightSeverity severity) {
    //noinspection deprecation
    return severity == HighlightSeverity.ERROR? HighlightInfoType.ERROR :
           severity == HighlightSeverity.WARNING ? HighlightInfoType.WARNING :
           severity == HighlightSeverity.INFO ? HighlightInfoType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? HighlightInfoType.WEAK_WARNING :
           severity ==HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING ? HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER :
           HighlightInfoType.INFORMATION;
  }

  @NotNull
  public static ProblemHighlightType convertType(@NotNull HighlightInfoType infoType) {
    if (infoType == HighlightInfoType.ERROR || infoType == HighlightInfoType.WRONG_REF) return ProblemHighlightType.ERROR;
    if (infoType == HighlightInfoType.WARNING) return ProblemHighlightType.WARNING;
    if (infoType == HighlightInfoType.INFORMATION) return ProblemHighlightType.INFORMATION;
    return ProblemHighlightType.WEAK_WARNING;
  }

  @NotNull
  public static ProblemHighlightType convertSeverityToProblemHighlight(@NotNull HighlightSeverity severity) {
    //noinspection deprecation
    return severity == HighlightSeverity.ERROR ? ProblemHighlightType.ERROR :
           severity == HighlightSeverity.WARNING ? ProblemHighlightType.WARNING :
           severity == HighlightSeverity.INFO ? ProblemHighlightType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
  }

  public boolean hasHint() {
    return isFlagSet(HAS_HINT_MASK);
  }

  void setHint(boolean hasHint) {
    setFlag(HAS_HINT_MASK, hasHint);
  }

  public int getActualStartOffset() {
    RangeHighlighterEx h = highlighter;
    return h == null || !h.isValid() ? startOffset : h.getStartOffset();
  }
  public int getActualEndOffset() {
    RangeHighlighterEx h = highlighter;
    return h == null || !h.isValid() ? endOffset : h.getEndOffset();
  }

  public static class IntentionActionDescriptor {
    private final IntentionAction myAction;
    private volatile List<IntentionAction> myOptions;
    private volatile HighlightDisplayKey myKey;
    private final ProblemGroup myProblemGroup;
    private final HighlightSeverity mySeverity;
    private final String myDisplayName;
    private final Icon myIcon;
    private Boolean myCanCleanup;

    IntentionActionDescriptor(@NotNull IntentionAction action, List<IntentionAction> options, String displayName) {
      this(action, options, displayName, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action, Icon icon) {
      this(action, null, null, icon);
    }

    IntentionActionDescriptor(@NotNull IntentionAction action,
                              @Nullable List<IntentionAction> options,
                              @Nullable String displayName,
                              @Nullable Icon icon) {
      this(action, options, displayName, icon, null, null, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action,
                                     @Nullable List<IntentionAction> options,
                                     @Nullable String displayName,
                                     @Nullable Icon icon,
                                     @Nullable HighlightDisplayKey key,
                                     @Nullable ProblemGroup problemGroup,
                                     @Nullable HighlightSeverity severity) {
      myAction = action;
      myOptions = options;
      myDisplayName = displayName;
      myIcon = icon;
      myKey = key;
      myProblemGroup = problemGroup;
      mySeverity = severity;
    }

    @NotNull
    public IntentionAction getAction() {
      return myAction;
    }

    boolean isError() {
      return mySeverity == null || mySeverity.compareTo(HighlightSeverity.ERROR) >= 0;
    }
    
    boolean isInformation() {
      return HighlightSeverity.INFORMATION.equals(mySeverity);
    }

    boolean canCleanup(@NotNull PsiElement element) {
      if (myCanCleanup == null) {
        InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
        HighlightDisplayKey key = myKey;
        if (key == null) {
          myCanCleanup = false;
        }
        else {
          InspectionToolWrapper toolWrapper = profile.getInspectionTool(key.toString(), element);
          myCanCleanup = toolWrapper != null && toolWrapper.isCleanupTool();
        }
      }
      return myCanCleanup;
    }

    @Nullable
    public List<IntentionAction> getOptions(@NotNull PsiElement element, @Nullable Editor editor) {
      if (editor != null && Boolean.FALSE.equals(editor.getUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY))) {
        return null;
      }
      List<IntentionAction> options = myOptions;
      HighlightDisplayKey key = myKey;
      if (myProblemGroup != null) {
        String problemName = myProblemGroup.getProblemName();
        HighlightDisplayKey problemGroupKey = problemName != null ? HighlightDisplayKey.findById(problemName) : null;
        if (problemGroupKey != null) {
          key = problemGroupKey;
        }
      }
      if (options != null || key == null) {
        return options;
      }
      IntentionManager intentionManager = IntentionManager.getInstance();
      List<IntentionAction> newOptions = intentionManager.getStandardIntentionOptions(key, element);
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
      InspectionToolWrapper toolWrapper = profile.getInspectionTool(key.toString(), element);
      if (!(toolWrapper instanceof LocalInspectionToolWrapper)) {
        HighlightDisplayKey idKey = HighlightDisplayKey.findById(key.toString());
        if (idKey != null) {
          toolWrapper = profile.getInspectionTool(idKey.toString(), element);
        }
      }
      if (toolWrapper != null) {

        myCanCleanup = toolWrapper.isCleanupTool();

        IntentionAction fixAllIntention = intentionManager.createFixAllIntention(toolWrapper, myAction);
        InspectionProfileEntry wrappedTool = toolWrapper instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)toolWrapper).getTool()
                                                                                               : ((GlobalInspectionToolWrapper)toolWrapper).getTool();
        if (wrappedTool instanceof DefaultHighlightVisitorBasedInspection.AnnotatorBasedInspection) {
          List<IntentionAction> actions = Collections.emptyList();
          if (myProblemGroup instanceof SuppressableProblemGroup) {
            actions = Arrays.asList(((SuppressableProblemGroup)myProblemGroup).getSuppressActions(element));
          }
          if (fixAllIntention != null) {
            if (actions.isEmpty()) {
              return Collections.singletonList(fixAllIntention);
            }
            else {
              actions = new ArrayList<>(actions);
              actions.add(fixAllIntention);
            }
          }
          return actions;
        }
        ContainerUtil.addIfNotNull(newOptions, fixAllIntention);
        if (wrappedTool instanceof CustomSuppressableInspectionTool) {
          IntentionAction[] suppressActions = ((CustomSuppressableInspectionTool)wrappedTool).getSuppressActions(element);
          if (suppressActions != null) {
            ContainerUtil.addAll(newOptions, suppressActions);
          }
        }
        else {
          SuppressQuickFix[] suppressFixes = wrappedTool.getBatchSuppressActions(element);
          if (suppressFixes.length > 0) {
            newOptions.addAll(ContainerUtil.map(suppressFixes, SuppressIntentionActionFromFix::convertBatchToSuppressIntentionAction));
          }
        }

      }
      if (myProblemGroup instanceof SuppressableProblemGroup) {
        IntentionAction[] suppressActions = ((SuppressableProblemGroup)myProblemGroup).getSuppressActions(element);
        ContainerUtil.addAll(newOptions, suppressActions);
      }

      //noinspection SynchronizeOnThis
      synchronized (this) {
        options = myOptions;
        if (options == null) {
          myOptions = options = newOptions;
        }
        myKey = null;
      }

      return options;
    }

    @Nullable
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public String toString() {
      String text = getAction().getText();
      return "descriptor: " + (text.isEmpty() ? getAction().getClass() : text);
    }

    @Nullable
    public Icon getIcon() {
      return myIcon;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof IntentionActionDescriptor && myAction.equals(((IntentionActionDescriptor)obj).myAction);
    }
  }

  @Override
  public int getStartOffset() {
    return getActualStartOffset();
  }

  @Override
  public int getEndOffset() {
    return getActualEndOffset();
  }

  int getGroup() {
    return group;
  }

  boolean isFromInjection() {
    return isFlagSet(FROM_INJECTION_MASK);
  }

  @NotNull
  public String getText() {
    if (isFileLevelAnnotation()) return "";
    RangeHighlighterEx highlighter = this.highlighter;
    if (highlighter == null) {
      throw new RuntimeException("info not applied yet");
    }
    if (!highlighter.isValid()) return "";
    return highlighter.getDocument().getText(TextRange.create(highlighter));
  }

  public void registerFix(@Nullable IntentionAction action,
                          @Nullable List<IntentionAction> options,
                          @Nullable String displayName,
                          @Nullable TextRange fixRange,
                          @Nullable HighlightDisplayKey key) {
    if (action == null) return;
    if (fixRange == null) fixRange = new TextRange(startOffset, endOffset);
    if (quickFixActionRanges == null) {
      quickFixActionRanges = ContainerUtil.createLockFreeCopyOnWriteList();
    }
    IntentionActionDescriptor desc = new IntentionActionDescriptor(action, options, displayName, null, key, getProblemGroup(), getSeverity());
    quickFixActionRanges.add(Pair.create(desc, fixRange));
    fixStartOffset = Math.min (fixStartOffset, fixRange.getStartOffset());
    fixEndOffset = Math.max (fixEndOffset, fixRange.getEndOffset());
    if (action instanceof HintAction) {
      setHint(true);
    }
  }

  public void unregisterQuickFix(@NotNull Condition<? super IntentionAction> condition) {
    if (quickFixActionRanges != null) {
      quickFixActionRanges.removeIf(pair -> condition.value(pair.first.getAction()));
    }
  }
}