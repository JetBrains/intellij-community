// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.actions.DisableHighlightingIntentionAction;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.*;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiFunction;

import static com.intellij.openapi.util.NlsContexts.DetailedDescription;
import static com.intellij.openapi.util.NlsContexts.Tooltip;

@ApiStatus.NonExtendable
public class HighlightInfo implements Segment {
  private static final Logger LOG = Logger.getInstance(HighlightInfo.class);

  // optimization: if tooltip contains this marker object, then it replaced with description field in getTooltip()
  private static final String DESCRIPTION_PLACEHOLDER = "\u0000";

  private static final byte HAS_HINT_MASK = 0x1;
  private static final byte FROM_INJECTION_MASK = 0x2;
  private static final byte AFTER_END_OF_LINE_MASK = 0x4;
  private static final byte FILE_LEVEL_ANNOTATION_MASK = 0x8;
  private static final byte NEEDS_UPDATE_ON_TYPING_MASK = 0x10;
  /** true if this HighlightInfo was created as an error for some unresolved reference, so there likely will be some "Import" quickfixes after {@link com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider} being asked about em */
  private static final byte UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK = 0x20;

  // this HighlightInfo was created during visiting PsiElement 'element' with element.getTextRange() = TextRange(startOffset+visitingRangeDeltaStartOffset, endOffset+visitingRangeDeltaEndOffset)
  private int visitingRangeDeltaStartOffset;
  private int visitingRangeDeltaEndOffset;

  @MagicConstant(intValues = {HAS_HINT_MASK, FROM_INJECTION_MASK, AFTER_END_OF_LINE_MASK, FILE_LEVEL_ANNOTATION_MASK, NEEDS_UPDATE_ON_TYPING_MASK,
    UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK})
  private @interface FlagConstant {
  }

  public final TextAttributes forcedTextAttributes;
  public final TextAttributesKey forcedTextAttributesKey;
  @NotNull
  public final HighlightInfoType type;
  public final int startOffset;
  public final int endOffset;

  /**
   * @deprecated use {@link #findRegisteredQuickFix(BiFunction)} instead
   */
  @Deprecated
  public List<Pair<IntentionActionDescriptor, TextRange>> quickFixActionRanges;
  /**
   * @deprecated use {@link #findRegisteredQuickFix(BiFunction)} instead
   */
  @Deprecated
  public List<Pair<IntentionActionDescriptor, RangeMarker>> quickFixActionMarkers;

  /**
   * Find the quickfix (among ones added by {@link #registerFix}) selected by returning non-null value from the {@code predicate}
   * and return that value, or null if the quickfix was not found.
   */
  public <T> T findRegisteredQuickFix(@NotNull BiFunction<? super @NotNull IntentionActionDescriptor, ? super @NotNull TextRange, ? extends @Nullable T> predicate) {
    Set<IntentionActionDescriptor> processed = new HashSet<>();
    List<Pair<IntentionActionDescriptor, RangeMarker>> markers;
    List<Pair<IntentionActionDescriptor, TextRange>> ranges;
    synchronized (this) {
      markers = quickFixActionMarkers;
      ranges = quickFixActionRanges;
    }
    // prefer range markers as having more actual offsets
    T result = find(markers, processed, predicate);
    if (result != null) return result;
    return find(ranges, processed, predicate);
  }

  @Nullable
  private static <T> T find(@Nullable List<? extends Pair<IntentionActionDescriptor, ? extends Segment>> markers,
                            @NotNull Set<? super IntentionActionDescriptor> processed,
                            @NotNull BiFunction<? super @NotNull IntentionActionDescriptor, ? super @NotNull TextRange, ? extends T> predicate) {
    if (markers != null) {
      for (Pair<IntentionActionDescriptor, ? extends Segment> pair : markers) {
        Segment segment = pair.second;
        TextRange range = segment instanceof RangeMarker ? ((RangeMarker)segment).isValid() ? ((RangeMarker)segment).getTextRange() : null : (TextRange)segment;
        if (range == null) continue;
        IntentionActionDescriptor descriptor = pair.first;
        if (!processed.add(descriptor)) continue;
        T result = predicate.apply(descriptor, range);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private final @DetailedDescription String description;
  private final @Tooltip String toolTip;
  @NotNull
  private final HighlightSeverity severity;
  private final GutterMark gutterIconRenderer;
  private final ProblemGroup myProblemGroup;
  private final String inspectionToolId;
  private int group;
  /**
   * Quick fix text range: the range within which the Alt-Enter should open the quick fix popup.
   * Might be bigger than (getStartOffset(), getEndOffset()) when it's deemed more usable,
   * e.g., when "import class" fix wanted to be Alt-Entered from everywhere at the same line.
   *
   */
  private long fixRange;
  /**
   * @see FlagConstant for allowed values
   */
  private volatile byte myFlags;

  final int navigationShift;

  private @Nullable Object fileLevelComponentsStorage;

  @Nullable("null means it the same as highlighter")
  private RangeMarker fixMarker;
  volatile RangeHighlighterEx highlighter; // modified in EDT only
  @Nullable
  final PsiElement psiElement;
  /**
   * in case this HighlightInfo is created to highlight unresolved reference, store this reference here to be able to call {@link com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider} later
   */
  final PsiReference unresolvedReference;

  /**
   * @deprecated Do not create manually, use {@link #newHighlightInfo(HighlightInfoType)} instead
   */
  @Deprecated
  @ApiStatus.Internal
  protected HighlightInfo(@Nullable TextAttributes forcedTextAttributes,
                          @Nullable TextAttributesKey forcedTextAttributesKey,
                          @NotNull HighlightInfoType type,
                          int startOffset,
                          int endOffset,
                          @Nullable @DetailedDescription String escapedDescription,
                          @Nullable @Tooltip String escapedToolTip,
                          @NotNull HighlightSeverity severity,
                          boolean afterEndOfLine,
                          @Nullable Boolean needsUpdateOnTyping,
                          boolean isFileLevelAnnotation,
                          int navigationShift,
                          ProblemGroup problemGroup,
                          @Nullable String inspectionToolId,
                          GutterMark gutterIconRenderer,
                          int group,
                          @Nullable PsiReference unresolvedReference,
                          @Nullable PsiElement psiElement) {
    if (startOffset < 0 || startOffset > endOffset) {
      LOG.error("Incorrect highlightInfo bounds. description="+escapedDescription+"; startOffset="+startOffset+"; endOffset="+endOffset+";type="+type);
    }
    this.forcedTextAttributes = forcedTextAttributes;
    this.forcedTextAttributesKey = forcedTextAttributesKey;
    this.type = type;
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    fixRange = TextRangeScalarUtil.toScalarRange(startOffset, endOffset);
    description = escapedDescription;
    // optimization: do not retain extra memory if we can recompute
    toolTip = encodeTooltip(escapedToolTip, escapedDescription);
    this.severity = severity;
    myFlags = (byte)((afterEndOfLine ? AFTER_END_OF_LINE_MASK : 0) |
                     (calcNeedUpdateOnTyping(needsUpdateOnTyping, type) ? NEEDS_UPDATE_ON_TYPING_MASK : 0) |
                     (isFileLevelAnnotation ? FILE_LEVEL_ANNOTATION_MASK : 0));
    this.navigationShift = navigationShift;
    myProblemGroup = problemGroup;
    this.gutterIconRenderer = gutterIconRenderer;
    this.inspectionToolId = inspectionToolId;
    this.group = group;
    this.unresolvedReference = unresolvedReference;
    this.psiElement = psiElement;
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
  TextRange getFixTextRange() {
    return TextRangeScalarUtil.create(fixRange);
  }

  void markFromInjection() {
    setFlag(FROM_INJECTION_MASK, true);
  }

  void addFileLeverComponent(@NotNull FileEditor fileEditor, @NotNull JComponent component) {
    if (fileLevelComponentsStorage == null) {
      fileLevelComponentsStorage = new Pair<>(fileEditor, component);
    }
    else if (fileLevelComponentsStorage instanceof Pair) {
      //noinspection unchecked
      Pair<FileEditor, JComponent> pair = (Pair<FileEditor, JComponent>)fileLevelComponentsStorage;
      Map<FileEditor, JComponent> map = new HashMap<>();
      map.put(pair.first, pair.second);
      map.put(fileEditor, component);
      fileLevelComponentsStorage = map;
    }
    else if (fileLevelComponentsStorage instanceof Map) {
      //noinspection unchecked
      ((Map<FileEditor, JComponent>)fileLevelComponentsStorage).put(fileEditor, component);
    }
    else {
      LOG.error(new IllegalStateException("fileLevelComponents=" + fileLevelComponentsStorage));
    }
  }

  void removeFileLeverComponent(@NotNull FileEditor fileEditor) {
    if (fileLevelComponentsStorage instanceof Pair) {
      //noinspection unchecked
      Pair<FileEditor, JComponent> pair = (Pair<FileEditor, JComponent>)fileLevelComponentsStorage;
      if (pair.first == fileEditor) {
        fileLevelComponentsStorage = null;
      }
    }
    else if (fileLevelComponentsStorage instanceof Map) {
      //noinspection unchecked
      ((Map<FileEditor, JComponent>)fileLevelComponentsStorage).remove(fileEditor);
    }
  }

  @Nullable JComponent getFileLevelComponent(@NotNull FileEditor fileEditor) {
    if (fileLevelComponentsStorage == null) {
      return null;
    }
    else if (fileLevelComponentsStorage instanceof Pair) {
      //noinspection unchecked
      Pair<FileEditor, JComponent> pair = (Pair<FileEditor, JComponent>)fileLevelComponentsStorage;
      return pair.first == fileEditor ? pair.second : null;
    }
    else if (fileLevelComponentsStorage instanceof Map) {
      //noinspection unchecked
      return ((Map<FileEditor, JComponent>)fileLevelComponentsStorage).get(fileEditor);
    }
    else {
      LOG.error(new IllegalStateException("fileLevelComponents=" + fileLevelComponentsStorage));
      return null;
    }
  }

  @Nullable
  public @Tooltip String getToolTip() {
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
   * @param tooltip     - html text
   * @param description - plain text (not escaped)
   * @return encoded tooltip (stripped html text with one or more placeholder characters)
   * or tooltip without changes.
   */
  private static @Tooltip String encodeTooltip(@Tooltip String tooltip, @DetailedDescription String description) {
    if (tooltip == null || description == null || description.isEmpty()) return tooltip;

    String encoded = StringUtil.replace(tooltip, XmlStringUtil.escapeString(description), DESCRIPTION_PLACEHOLDER);
    if (Strings.areSameInstance(encoded, tooltip)) {
      return tooltip;
    }
    if (encoded.equals(DESCRIPTION_PLACEHOLDER)) encoded = DESCRIPTION_PLACEHOLDER;
    return XmlStringUtil.stripHtml(encoded);
  }

  public @DetailedDescription String getDescription() {
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
    //noinspection NonAtomicOperationOnVolatileField
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  boolean isFileLevelAnnotation() {
    return isFlagSet(FILE_LEVEL_ANNOTATION_MASK);
  }

  void setVisitingTextRange(long range) {
    visitingRangeDeltaStartOffset = TextRangeScalarUtil.startOffset(range) - getStartOffset();
    visitingRangeDeltaEndOffset = TextRangeScalarUtil.endOffset(range) - getEndOffset();
  }

  long getVisitingTextRange() {
    int visitStart = getActualStartOffset() + visitingRangeDeltaStartOffset;
    int visitEnd = getActualEndOffset() + visitingRangeDeltaEndOffset;
    return TextRange.isProperRange(visitStart, visitEnd) ? TextRangeScalarUtil.toScalarRange(visitStart, visitEnd) : -1;
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
  Color getErrorStripeMarkColor(@NotNull PsiElement element,
                                @Nullable("when null, the global scheme will be used") EditorColorsScheme colorsScheme) {
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
    //noinspection deprecation
    if (getSeverity() == HighlightSeverity.INFO) {
      //noinspection deprecation
      return scheme.getAttributes(CodeInsightColors.INFO_ATTRIBUTES).getErrorStripeColor();
    }
    if (getSeverity() == HighlightSeverity.WEAK_WARNING) {
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

  public boolean needUpdateOnTyping() {
    return isFlagSet(NEEDS_UPDATE_ON_TYPING_MASK);
  }

  private static boolean calcNeedUpdateOnTyping(@Nullable Boolean needsUpdateOnTyping, HighlightInfoType type) {
    if (needsUpdateOnTyping != null) {
      return needsUpdateOnTyping;
    }
    if (type instanceof HighlightInfoType.UpdateOnTypingSuppressible) {
      return ((HighlightInfoType.UpdateOnTypingSuppressible)type).needsUpdateOnTyping();
    }
    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof HighlightInfo info)) return false;

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
  public @NonNls String toString() {
    String s = "HighlightInfo(" + startOffset + "," + endOffset + ")";
    if (getActualStartOffset() != startOffset || getActualEndOffset() != endOffset) {
      s += "; actual: (" + getActualStartOffset() + "," + getActualEndOffset() + ")";
    }
    if (highlighter != null) s += " text='" + getText() + "'";
    if (getDescription() != null) s += ", description='" + getDescription() + "'";
    s += "; severity=" + getSeverity();
    s += "; group=" + getGroup();
    synchronized (this) {
      if (quickFixActionRanges != null) {
        s += "; quickFixes: " + quickFixActionRanges;
      }
    }
    if (gutterIconRenderer != null) {
      s += "; gutter: " + gutterIconRenderer;
    }
    return s;
  }

  @NotNull
  public static Builder newHighlightInfo(@NotNull HighlightInfoType type) {
    return new HighlightInfoB(type);
  }

  void setGroup(int group) {
    this.group = group;
  }

  @ApiStatus.NonExtendable
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

    @NotNull
    Builder registerFix(@NotNull IntentionAction action,
                        @Nullable List<? extends IntentionAction> options,
                        @Nullable @Nls String displayName,
                        @Nullable TextRange fixRange,
                        @Nullable HighlightDisplayKey key);

    @NotNull
    default Builder registerFix(@NotNull ModCommandAction action,
                        @Nullable List<? extends IntentionAction> options,
                        @Nullable @Nls String displayName,
                        @Nullable TextRange fixRange,
                        @Nullable HighlightDisplayKey key) {
      return registerFix(action.asIntention(), options, displayName, fixRange, key);
    }

    @Nullable("null means filtered out")
    HighlightInfo create();

    @NotNull
    HighlightInfo createUnconditionally();
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

    PsiReference unresolvedReference = annotation.getUnresolvedReference();
    PsiElement psiElement = unresolvedReference == null ? null : unresolvedReference.getElement();
    HighlightInfo info = new HighlightInfo(
      forcedAttributes, forcedAttributesKey, convertType(annotation), annotation.getStartOffset(), annotation.getEndOffset(),
      annotation.getMessage(), annotation.getTooltip(), annotation.getSeverity(), annotation.isAfterEndOfLine(),
      annotation.needsUpdateOnTyping(),
      annotation.isFileLevelAnnotation(), 0, annotation.getProblemGroup(), null, annotation.getGutterIconRenderer(), Pass.UPDATE_ALL,
      unresolvedReference, psiElement);

    List<? extends Annotation.QuickFixInfo> fixes = batchMode ? annotation.getBatchFixes() : annotation.getQuickFixes();
    if (fixes != null) {
      for (Annotation.QuickFixInfo quickFixInfo : fixes) {
        TextRange range = quickFixInfo.textRange;
        HighlightDisplayKey k = quickFixInfo.key != null ? quickFixInfo.key
                                                         : HighlightDisplayKey.find(HighlightVisitorBasedInspection.SHORT_NAME);
        info.registerFix(quickFixInfo.quickFix, null, HighlightDisplayKey.getDisplayNameByKey(k), range, k);
      }
    }

    return info;
  }

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
    if (problemHighlightType == ProblemHighlightType.POSSIBLE_PROBLEM) return HighlightInfoType.POSSIBLE_PROBLEM;
    return convertSeverity(severity);
  }

  @NotNull
  public static HighlightInfoType convertSeverity(@NotNull HighlightSeverity severity) {
    //noinspection deprecation
    return severity == HighlightSeverity.ERROR ? HighlightInfoType.ERROR :
           severity == HighlightSeverity.WARNING ? HighlightInfoType.WARNING :
           severity == HighlightSeverity.INFO ? HighlightInfoType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? HighlightInfoType.WEAK_WARNING :
           severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING ? HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER :
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
    //noinspection deprecation,removal
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
    volatile List<? extends IntentionAction> myOptions;
    volatile HighlightDisplayKey myKey;
    private final ProblemGroup myProblemGroup;
    private final HighlightSeverity mySeverity;
    private final @Nls String myDisplayName;
    private final Icon myIcon;
    private Boolean myCanCleanup;

    public IntentionActionDescriptor(@NotNull IntentionAction action,
                                     @Nullable List<? extends IntentionAction> options,
                                     @Nullable @Nls String displayName,
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

    @Nullable IntentionActionDescriptor copyWithEmptyAction() {
      if (myKey == null || myKey.getID().equals(HighlightVisitorBasedInspection.SHORT_NAME)) {
        // No need to show "Inspection 'Annotator' options" quick fix, it wouldn't be actionable.
        return null;
      }

      String displayName = HighlightDisplayKey.getDisplayNameByKey(myKey);
      if (displayName == null) return null;
      return new IntentionActionDescriptor(new EmptyIntentionAction(displayName), myOptions, myDisplayName, myIcon,
                                           myKey, myProblemGroup, mySeverity);
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
          InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(key.toString(), element);
          myCanCleanup = toolWrapper != null && toolWrapper.isCleanupTool();
        }
      }
      return myCanCleanup;
    }

    @NotNull
    public Iterable<? extends IntentionAction> getOptions(@NotNull PsiElement element, @Nullable Editor editor) {
      if (editor != null && Boolean.FALSE.equals(editor.getUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY))) {
        return Collections.emptyList();
      }
      List<? extends IntentionAction> options = myOptions;
      if (options != null) {
        return options;
      }
      HighlightDisplayKey key = myKey;
      if (myProblemGroup != null) {
        String problemName = myProblemGroup.getProblemName();
        HighlightDisplayKey problemGroupKey = problemName != null ? HighlightDisplayKey.findById(problemName) : null;
        if (problemGroupKey != null) {
          key = problemGroupKey;
        }
      }
      IntentionAction action = IntentionActionDelegate.unwrap(myAction);
      if (action instanceof IntentionActionWithOptions wo) {
        if (key == null || wo.getCombiningPolicy() == IntentionActionWithOptions.CombiningPolicy.IntentionOptionsOnly) {
          options = wo.getOptions();
          if (!options.isEmpty()) {
            return updateOptions(options);
          }
        }
      }

      if (key == null) {
        return Collections.emptyList();
      }

      IntentionManager intentionManager = IntentionManager.getInstance();
      List<IntentionAction> newOptions = intentionManager.getStandardIntentionOptions(key, element);
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
      InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(key.toString(), element);
      if (!(toolWrapper instanceof LocalInspectionToolWrapper)) {
        HighlightDisplayKey idKey = HighlightDisplayKey.findById(key.toString());
        if (idKey != null) {
          toolWrapper = profile.getInspectionTool(idKey.toString(), element);
        }
      }
      if (toolWrapper != null) {
        myCanCleanup = toolWrapper.isCleanupTool();

        IntentionAction fixAllIntention = intentionManager.createFixAllIntention(toolWrapper, myAction);
        InspectionProfileEntry wrappedTool =
          toolWrapper instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)toolWrapper).getTool()
                                                            : ((GlobalInspectionToolWrapper)toolWrapper).getTool();
        if (wrappedTool instanceof HighlightVisitorBasedInspection) {
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

        if (!(action instanceof EmptyIntentionAction)) {
          newOptions.add(new DisableHighlightingIntentionAction(toolWrapper.getShortName()));
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

      return updateOptions(newOptions);
    }

    @NotNull
    private synchronized List<? extends IntentionAction> updateOptions(@NotNull List<? extends IntentionAction> newOptions) {
      List<? extends IntentionAction> options = myOptions;
      if (options == null) {
        myOptions = options = newOptions;
      }
      myKey = null;
      return options;
    }

    @Nullable
    public @Nls String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public String toString() {
      String text = getAction().getText();
      return "IntentionActionDescriptor: " + (text.isEmpty() ? IntentionActionDelegate.unwrap(getAction()).getClass() : text + " (" + IntentionActionDelegate.unwrap(getAction()).getClass() + ")");
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
    return highlighter.getDocument().getText(highlighter.getTextRange());
  }

  /**
   * @deprecated Use {@link Builder#registerFix(IntentionAction, List, String, TextRange, HighlightDisplayKey)} instead
   */
  @Deprecated
  public synchronized // synchronized to avoid concurrent access to quickFix* fields; TODO rework to lock-free
  void registerFix(@Nullable IntentionAction action,
                          @Nullable List<? extends IntentionAction> options,
                          @Nullable @Nls String displayName,
                          @Nullable TextRange fixRange,
                          @Nullable HighlightDisplayKey key) {
    if (action == null) return;
    if (fixRange == null) fixRange = new TextRange(getActualStartOffset(), getActualEndOffset());
    if (quickFixActionRanges == null) {
      quickFixActionRanges = ContainerUtil.createLockFreeCopyOnWriteList();
    }
    IntentionActionDescriptor desc =
      new IntentionActionDescriptor(action, options, displayName, null, key, getProblemGroup(), getSeverity());
    quickFixActionRanges.add(Pair.create(desc, fixRange));
    if (fixMarker != null && fixMarker.isValid()) {
      this.fixRange = TextRangeScalarUtil.toScalarRange(fixMarker);
    }
    else {
      RangeMarker fixMarker = this.fixMarker;
      RangeHighlighterEx highlighter = this.highlighter;
      Document document = fixMarker != null ? fixMarker.getDocument() :
                          highlighter != null ? highlighter.getDocument() :
                          quickFixActionMarkers != null && !quickFixActionMarkers.isEmpty() && quickFixActionMarkers.get(0).getSecond() != null ? quickFixActionMarkers.get(0).getSecond().getDocument() :
                          null;
      if (document != null) {
        // coerce fixRange inside document
        int newEnd = Math.min(document.getTextLength(), TextRangeScalarUtil.endOffset(this.fixRange));
        int newStart = Math.min(newEnd, TextRangeScalarUtil.startOffset(this.fixRange));
        this.fixRange = TextRangeScalarUtil.toScalarRange(newStart, newEnd);
      }
    }
    this.fixRange = TextRangeScalarUtil.union(this.fixRange, TextRangeScalarUtil.toScalarRange(fixRange));
    if (action instanceof HintAction) {
      setHint(true);
    }
    RangeHighlighterEx myHighlighter = highlighter;
    if (myHighlighter != null && myHighlighter.isValid()) {
      // highlighter already has been created, we need to update quickFixActionMarkers
      long highlighterRange = TextRangeScalarUtil.toScalarRange(myHighlighter);
      Long2ObjectMap<RangeMarker> cache = reuseRangeMarkerCacheIfCreated(highlighterRange);
      updateQuickFixFields(myHighlighter.getDocument(), cache, highlighterRange);
    }
  }

  @NotNull
  private Long2ObjectMap<RangeMarker> reuseRangeMarkerCacheIfCreated(long targetRange) {
    Long2ObjectMap<RangeMarker> cache = new Long2ObjectOpenHashMap<>();
    if (quickFixActionMarkers != null) {
      for (Pair<IntentionActionDescriptor, RangeMarker> pair : quickFixActionMarkers) {
        RangeMarker marker = pair.getSecond();
        if (marker.isValid() && TextRangeScalarUtil.toScalarRange(marker) == targetRange) {
          cache.put(targetRange, marker);
          break;
        }
      }
    }
    return cache;
  }

  public synchronized //TODO rework to lock-free
  void unregisterQuickFix(@NotNull Condition<? super IntentionAction> condition) {
    if (quickFixActionRanges != null) {
      quickFixActionRanges.removeIf(pair -> condition.value(pair.first.getAction()));
    }
    if (quickFixActionMarkers != null) {
      quickFixActionMarkers.removeIf(pair -> condition.value(pair.first.getAction()));
    }
  }

  public synchronized IntentionAction getSameFamilyFix(@NotNull IntentionActionWithFixAllOption action) {
    if (quickFixActionRanges == null) return null;
    for (Pair<IntentionActionDescriptor, TextRange> range : quickFixActionRanges) {
      IntentionAction other = IntentionActionDelegate.unwrap(range.first.myAction);
      if (other instanceof IntentionActionWithFixAllOption &&
          action.belongsToMyFamily((IntentionActionWithFixAllOption)other)) return other;
    }
    return null;
  }


  boolean containsOffset(int offset, boolean includeFixRange) {
    RangeHighlighterEx highlighter = getHighlighter();
    if (highlighter == null || !highlighter.isValid()) return false;
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (startOffset <= offset && offset <= endOffset) {
      return true;
    }
    if (!includeFixRange) return false;
    RangeMarker fixMarker = this.fixMarker;
    if (fixMarker != null) {  // null means its range is the same as highlighter
      if (!fixMarker.isValid()) return false;
      startOffset = fixMarker.getStartOffset();
      endOffset = fixMarker.getEndOffset();
      return startOffset <= offset && offset <= endOffset;
    }
    return TextRangeScalarUtil.containsOffset(fixRange, offset);
  }
  @NotNull
  private static RangeMarker getOrCreate(@NotNull Document document,
                                         @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                         long textRange) {
    return range2markerCache.computeIfAbsent(textRange, __ -> document.createRangeMarker(TextRangeScalarUtil.startOffset(textRange),
                                                                                           TextRangeScalarUtil.endOffset(textRange)));
  }

  // convert ranges to markers: from quickFixRanges -> quickFixMarkers and fixRange -> fixMarker
  // TODO rework to lock-free
  synchronized void updateQuickFixFields(@NotNull Document document,
                                         @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                         long finalHighlighterRange) {
    if (quickFixActionMarkers != null && quickFixActionRanges != null && quickFixActionRanges.size() == quickFixActionMarkers.size() +1) {
      // markers already created, make quickFixRanges <-> quickFixMarkers consistent by adding new marker to the quickFixMarkers if necessary
      Pair<IntentionActionDescriptor, TextRange> last = ContainerUtil.getLastItem(quickFixActionRanges);
      Segment textRange = last.getSecond();
      if (textRange.getEndOffset() <= document.getTextLength()) {
        RangeMarker marker = getOrCreate(document, range2markerCache, TextRangeScalarUtil.toScalarRange(textRange));
        quickFixActionMarkers.add(Pair.create(last.getFirst(), marker));
      }
      return;
    }
    if (quickFixActionRanges != null && quickFixActionMarkers == null) {
      List<Pair<IntentionActionDescriptor, RangeMarker>> list = new ArrayList<>(quickFixActionRanges.size());
      for (Pair<IntentionActionDescriptor, TextRange> pair : quickFixActionRanges) {
        TextRange textRange = pair.second;
        if (textRange.getEndOffset() > document.getTextLength()) continue;
        RangeMarker marker = getOrCreate(document, range2markerCache, TextRangeScalarUtil.toScalarRange(textRange));
        list.add(Pair.create(pair.first, marker));
      }
      quickFixActionMarkers = ContainerUtil.createLockFreeCopyOnWriteList(list);
    }
    if (fixRange == finalHighlighterRange) {
      fixMarker = null; // null means it the same as highlighter's range
    }
    else if (TextRangeScalarUtil.endOffset(fixRange) <= document.getTextLength()) {
      fixMarker = getOrCreate(document, range2markerCache, fixRange);
    }
  }

  @ApiStatus.Internal
  boolean isUnresolvedReference() {
    return unresolvedReference != null || type.getAttributesKey().equals(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
  }

  boolean isUnresolvedReferenceQuickFixesComputed() {
    return isFlagSet(UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK);
  }
  void setUnresolvedReferenceQuickFixesComputed() {
    setFlag(UNRESOLVED_REFERENCE_QUICK_FIXES_COMPUTED_MASK, true);
  }
}