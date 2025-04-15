// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.actions.DisableHighlightingIntentionAction;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.*;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static com.intellij.openapi.util.NlsContexts.DetailedDescription;
import static com.intellij.openapi.util.NlsContexts.Tooltip;

@ApiStatus.NonExtendable
public class HighlightInfo implements Segment {
  private static final Logger LOG = Logger.getInstance(HighlightInfo.class);
  /**
   * Short name of the {@link com.intellij.codeInsight.daemon.impl.HighlightVisitorBasedInspection} tool, which needs to be treated differently from other inspections:
   * it doesn't have "disable" or "suppress" quickfixes
   */
  @ApiStatus.Internal
  public static final String ANNOTATOR_INSPECTION_SHORT_NAME = "Annotator";
  // optimization: if tooltip contains this marker object, then it replaced with description field in getTooltip()
  private static final String DESCRIPTION_PLACEHOLDER = "\u0000";

  private static final byte HAS_HINT_MASK = 0x1;
  private static final byte FROM_INJECTION_MASK = 0x2;
  private static final byte AFTER_END_OF_LINE_MASK = 0x4;
  private static final byte FILE_LEVEL_ANNOTATION_MASK = 0x8;
  private static final byte NEEDS_UPDATE_ON_TYPING_MASK = 0x10;

  @NotNull
  @Unmodifiable
  private synchronized List<IntentionActionDescriptor> getIntentionActionDescriptors() {
    return ContainerUtil.concat(myIntentionActionDescriptors, ContainerUtil.flatMap(myLazyQuickFixes, desc-> {
      if (desc.future() != null && desc.future().isDone()) {
        try {
          return List.copyOf(desc.future().get());
        }
        catch (InterruptedException | ExecutionException e) {
          LOG.warn(e);
          return List.of();
        }
      }
      else {
        return List.of();
      }
    }));
  }

  @MagicConstant(intValues = {HAS_HINT_MASK, FROM_INJECTION_MASK, AFTER_END_OF_LINE_MASK, FILE_LEVEL_ANNOTATION_MASK, NEEDS_UPDATE_ON_TYPING_MASK})
  private @interface FlagConstant {
  }

  public final TextAttributes forcedTextAttributes;
  public final TextAttributesKey forcedTextAttributesKey;
  public final @NotNull HighlightInfoType type;
  public final int startOffset;
  public final int endOffset;

  /**
   * @deprecated use {@link #findRegisteredQuickFix(BiFunction)} instead
   */
  @Deprecated public @Unmodifiable List<Pair<IntentionActionDescriptor, TextRange>> quickFixActionRanges;
  /**
   * @deprecated use {@link #findRegisteredQuickFix(BiFunction)} instead
   */
  @Deprecated public @Unmodifiable List<Pair<IntentionActionDescriptor, RangeMarker>> quickFixActionMarkers;

  private @Unmodifiable @NotNull List<IntentionActionDescriptor> myIntentionActionDescriptors = List.of(); // guarded by this
  // list of (code fragment to be executed in BGT, and their execution result future)
  // future == null means the code was never executed, not-null means the execution has started, .isDone() means it's completed
  private @NotNull @Unmodifiable List<LazyFixDescription> myLazyQuickFixes; // guarded by this
  private record LazyFixDescription(
    @NotNull Consumer<? super QuickFixActionRegistrar> fixesComputer,
    // 0 means the stamp not set yet
    long psiModificationStamp,
    // null means the computation is not started yet
    @Nullable Future<? extends @NotNull List<IntentionActionDescriptor>> future) {}

  private final @DetailedDescription String description;
  private final @Tooltip String toolTip;
  private final @NotNull HighlightSeverity severity;
  private final GutterMark gutterIconRenderer;
  private final ProblemGroup myProblemGroup;
  volatile Object toolId; // inspection.getShortName() in case when the inspection generated this info; Class<Annotator> in case of annotators, etc
  private int group;
  /**
   * Quick fix text range: the range within which the Alt-Enter should open the quick fix popup.
   * Might be bigger than (getStartOffset(), getEndOffset()) when it's deemed more usable,
   * e.g., when "import class" fix wanted to be Alt-Entered from everywhere at the same line.
   * During the creation of this {@link HighlightInfo} the fix range is accumulated here, and then moved to {@link #fixMarker},
   * when this {@link HighlightInfo} is associated with {@link Document}, to support updating its fix range along with the document changes.
   * After this point {@link #fixMarker} stores the actual fix range, and {@code fixRange} is not needed anymore.
   */
  private final long fixRange;
  /**
   * special value for {@link #fixMarker} which means its fix range is the same as the range of {@link #highlighter}.
   * Used as an optimization to conserve memory
   */
  private static final RangeMarker FIX_MARKER_SAME_AS_HIGHLIGHTER = FileStatusMap.WHOLE_FILE_DIRTY_MARKER;
  private @Nullable("null means it's the same as highlighter") RangeMarker fixMarker = FIX_MARKER_SAME_AS_HIGHLIGHTER;
  /**
   * @see FlagConstant for allowed values
   */
  private volatile byte myFlags;

  @ApiStatus.Internal
  public final int navigationShift;

  private @Nullable Object fileLevelComponentsStorage;

  private volatile RangeHighlighterEx highlighter;

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
                          @Nullable ProblemGroup problemGroup,
                          @Nullable Object toolId,
                          @Nullable GutterMark gutterIconRenderer,
                          int group,
                          boolean hasHint,
                          @NotNull @Unmodifiable List<? extends @NotNull Consumer<? super QuickFixActionRegistrar>> lazyFixes) {
    if (startOffset < 0 || startOffset > endOffset) {
      LOG.error("Incorrect highlightInfo bounds. description="+escapedDescription+"; startOffset="+startOffset+"; endOffset="+endOffset+";type="+type+". Maybe you forgot to call .range()?");
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
                     (isFileLevelAnnotation ? FILE_LEVEL_ANNOTATION_MASK : 0) |
                     (hasHint ? HAS_HINT_MASK : 0)
    );
    this.navigationShift = navigationShift;
    myProblemGroup = problemGroup;
    this.gutterIconRenderer = gutterIconRenderer;
    this.toolId = toolId;
    this.group = group;
    myLazyQuickFixes = ContainerUtil.map(lazyFixes, c->new LazyFixDescription(c,0, null));
  }

  @ApiStatus.Internal
  public void setToolId(Object toolId) {
    this.toolId = toolId;
  }

  @ApiStatus.Internal
  public Object getToolId() {
    return toolId;
  }

  /**
   * Find the quickfix (among ones added by {@link #registerFixes}) selected by returning non-null value from the {@code predicate}
   * and return that value, or null if the quickfix was not found.
   * @param predicate called with the found {@link IntentionActionDescriptor}, and its fix range, and returns a value.
   */
  public <T> T findRegisteredQuickFix(@NotNull BiFunction<? super @NotNull IntentionActionDescriptor, ? super @NotNull TextRange, ? extends @Nullable T> predicate) {
    List<IntentionActionDescriptor> descriptors = getIntentionActionDescriptors();
    Set<IntentionActionDescriptor> processed = new HashSet<>();
    for (IntentionActionDescriptor descriptor : descriptors) {
      if (!processed.add(descriptor)) continue;
      TextRange fixRange = descriptor.getFixRange();
      if (fixRange == null) {
        fixRange = TextRange.create(getFixTextRange());
      }
      T result = predicate.apply(descriptor, fixRange);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * Returns the HighlightInfo instance from which the given range highlighter was created, or null if there isn't any.
   */
  public static @Nullable HighlightInfo fromRangeHighlighter(@NotNull RangeHighlighter highlighter) {
    Object errorStripeTooltip = highlighter.getErrorStripeTooltip();
    return errorStripeTooltip instanceof HighlightInfo info ? info : null;
  }

  @NotNull
  @ApiStatus.Internal
  private synchronized Segment getFixTextRange() {
    RangeMarker myFixMarker = fixMarker;
    if (myFixMarker != null) {
      if (myFixMarker == FIX_MARKER_SAME_AS_HIGHLIGHTER) {
        RangeHighlighterEx myHighlighter = highlighter;
        if (myHighlighter != null && myHighlighter.isValid()) {
          return myHighlighter;
        }
      }
      else if (myFixMarker.isValid()) {
        return myFixMarker;
      }
    }
    return TextRangeScalarUtil.create(fixRange);
  }

  @ApiStatus.Internal
  public void markFromInjection() {
    setFlag(FROM_INJECTION_MASK, true);
  }

  @ApiStatus.Internal
  public void addFileLevelComponent(@NotNull FileEditor fileEditor, @NotNull JComponent component) {
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

  @ApiStatus.Internal
  public void removeFileLeverComponent(@NotNull FileEditor fileEditor) {
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

  @ApiStatus.Internal
  public @Nullable JComponent getFileLevelComponent(@NotNull FileEditor fileEditor) {
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

  public @Nullable @Tooltip String getToolTip() {
    String toolTip = this.toolTip;
    String description = this.description;

    if (toolTip == null) return null;

    String wrapped = XmlStringUtil.wrapInHtml(toolTip);
    if (description == null || !wrapped.contains(DESCRIPTION_PLACEHOLDER)) {
      return wrapped;
    }

    return StringUtil.replace(wrapped, DESCRIPTION_PLACEHOLDER, XmlStringUtil.escapeString(description));
  }

  /**
   * Encodes \p tooltip so that substrings equal to a \p description
   * are replaced with the special placeholder to reduce the size of the
   * tooltip. <html></html> tags are stripped of the tooltip.
   *
   * @param tooltip     - html text
   * @param description - plain text (not escaped)
   * @return encoded tooltip (stripped html text with one or more placeholder characters)
   * or tooltip without changes.
   */
  private static @Nullable @Tooltip String encodeTooltip(@Nullable @Tooltip String tooltip,
                                                         @Nullable @DetailedDescription String description) {
    if (tooltip == null) return null;

    String stripped = XmlStringUtil.stripHtml(tooltip);
    if (description == null || description.isEmpty()) return stripped;

    String encoded = StringUtil.replace(stripped, XmlStringUtil.escapeString(description), DESCRIPTION_PLACEHOLDER);
    if (Strings.areSameInstance(encoded, stripped)) {
      return stripped;
    }
    if (encoded.equals(DESCRIPTION_PLACEHOLDER)) encoded = DESCRIPTION_PLACEHOLDER;
    return encoded;
  }

  public @DetailedDescription String getDescription() {
    return description;
  }

  public @Nullable @NonNls String getInspectionToolId() {
    return toolId instanceof String inspectionToolShortName ? inspectionToolShortName : null;
  }

  @ApiStatus.Internal
  public @Nullable @NonNls String getExternalSourceId() {
    return myProblemGroup instanceof ExternalSourceProblemGroup ?
           ((ExternalSourceProblemGroup)myProblemGroup).getExternalCheckName() : null;
  }

  private boolean isFlagSet(@FlagConstant byte mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  private void setFlag(@FlagConstant byte mask, boolean value) {
    //noinspection NonAtomicOperationOnVolatileField
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  @ApiStatus.Internal
  public boolean isFileLevelAnnotation() {
    return isFlagSet(FILE_LEVEL_ANNOTATION_MASK);
  }

  /**
   * @deprecated do not use
   * todo remove along with DefaultHighlightInfoProcessor
   */
  @Deprecated
  void setVisitingTextRange(@NotNull PsiFile psiFile, @NotNull Document document, long range) {
  }

  /**
   * @deprecated todo remove along with DefaultHighlightInfoProcessor
   */
  @Deprecated
  @NotNull
  Segment getVisitingTextRange() {
    return TextRange.EMPTY_RANGE;
  }

  public @NotNull HighlightSeverity getSeverity() {
    return severity;
  }

  public RangeHighlighterEx getHighlighter() {
    return highlighter;
  }

  public synchronized void setHighlighter(@NotNull RangeHighlighterEx highlighter) {
    if (this.highlighter != null) {
      throw new IllegalStateException("Cannot set highlighter to " + highlighter+ " because it already set: "+this.highlighter+". Maybe this HighlightInfo was (incorrectly) stored and reused?");
    }
    this.highlighter = highlighter;
    // as soon as the HighlightInfo is bound to the document, we can replace TextRanges in IntentionActionDescriptor with RangeMarkers
    updateFields(getIntentionActionDescriptors(), highlighter.getDocument());
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors());
  }

  public boolean isAfterEndOfLine() {
    return isFlagSet(AFTER_END_OF_LINE_MASK);
  }

  public @Nullable TextAttributes getTextAttributes(@Nullable PsiElement element, @Nullable EditorColorsScheme editorColorsScheme) {
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

  private static @NotNull EditorColorsScheme getColorsScheme(@Nullable EditorColorsScheme customScheme) {
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

    return equalsByActualOffset(info);
  }

  @ApiStatus.Internal
  public boolean equalsByActualOffset(@NotNull HighlightInfo info) {
    if (info == this) return true;

    return info.getActualStartOffset() == getActualStartOffset() &&
           info.getActualEndOffset() == getActualEndOffset() &&
           attributesEqual(info);
  }

  @ApiStatus.Internal
  public boolean attributesEqual(@NotNull HighlightInfo info) {
    return info.getSeverity() == getSeverity() &&
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

  @ApiStatus.Internal
  public @NonNls String toStringCompact(boolean showFullQualifiedClassNames) {
    String s = "HighlightInfo(" + getStartOffset() + "," + getEndOffset() + ")";
    if (isFileLevelAnnotation()) {
      s+=" (file level)";
    }
    if (getStartOffset() != startOffset || getEndOffset() != endOffset) {
      s += "; created as: (" + startOffset + "," + endOffset + ")";
    }
    if (highlighter != null) {
      s += "; text='" + StringUtil.first(getText(), 40, true) + "'";
      if (!highlighter.isValid()) {
        s += "; highlighter: ("+highlighter.getStartOffset()+", "+highlighter.getEndOffset()+") is invalid";
      }
    }
    if (getDescription() != null) {
      s += ", description='" + getDescription() + "'";
    }
    s += "; severity=" + getSeverity();
    synchronized (this) {
      if (!getIntentionActionDescriptors().isEmpty()) {
        s += "; quickFixes: " + StringUtil.join(getIntentionActionDescriptors(), ", ");
      }
    }
    if (gutterIconRenderer != null) {
      s += "; gutter: " + gutterIconRenderer;
    }
    if (toolId != null) {
      s += showFullQualifiedClassNames ? "; toolId: " + toolId + " (" + toolId.getClass() + ")" :
           "; toolId: " + (toolId instanceof Class ? ((Class<?>)toolId).getSimpleName() : "not specified");
    }
    if (group != HighlightInfoUpdaterImpl.MANAGED_HIGHLIGHT_INFO_GROUP) {
      s += "; group: " + group;
    }
    if (forcedTextAttributesKey != null) {
      s += "; forcedTextAttributesKey: " + forcedTextAttributesKey;
    }
    if (forcedTextAttributes != null) {
      s += "; forcedTextAttributes: " + forcedTextAttributes;
    }
    return s;
  }

  @Override
  public @NonNls String toString() {
    return toStringCompact(true);
  }

  public static @NotNull Builder newHighlightInfo(@NotNull HighlightInfoType type) {
    return new HighlightInfoB(type);
  }

  @ApiStatus.Internal
  public void setGroup(int group) {
    this.group = group;
  }

  /**
   * Builder for creating instances of {@link HighlightInfo}. See {@link #newHighlightInfo(HighlightInfoType)}
   */
  @ApiStatus.NonExtendable
  public interface Builder {
    // only one 'range' call allowed
    @NotNull Builder range(@NotNull TextRange textRange);

    @NotNull Builder range(@NotNull ASTNode node);

    @NotNull Builder range(@NotNull PsiElement element);

    /**
     * @param element element to highlight
     * @param rangeInElement range within element,
     *                       meaning that the following must hold: {@code rangeInElement.getStartOffset() >= 0 && rangeInElement.getEndOffset() <= element.getTextRange().getLength()}
     * @return this builder
     */
    @NotNull Builder range(@NotNull PsiElement element, @NotNull TextRange rangeInElement);

    /**
     * @param element element to highlight
     * @param start absolute start offset in the file (not within element)
     * @param end absolute end offset in the file (not within element)
     * @return this builder
     */
    @NotNull Builder range(@NotNull PsiElement element, int start, int end);

    @NotNull Builder range(int start, int end);

    @NotNull Builder gutterIconRenderer(@NotNull GutterIconRenderer gutterIconRenderer);

    @NotNull Builder problemGroup(@NotNull ProblemGroup problemGroup);

    /**
     * @deprecated Do not use. Inspections set this id automatically when run
     */
    @Deprecated
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

    /**
     * Specifies the piece of computation ({@code quickFixComputer}) which could produce
     * quick fixes for this {@link HighlightInfo} via calling {@link QuickFixActionRegistrar#register} methods, once or several times.
     * Use this method for quick fixes that are too expensive to be registered via regular {@link #registerFix} method.
     * These lazy quick fixes registered here have different life cycle from the regular quick fixes:<br>
     * <li>They are computed only by request, for example when the user presses Alt-Enter to show all available quick fixes at the caret position</li>
     * <li>They could be computed in a different thread/time than the inspection/annotator which registered them</li>
     * Use this method for quick fixes that do noticeable work before being shown,
     * for example, a fix which tries to find a suitable binding for the unresolved reference under the caret.
     */
    @NotNull
    @ApiStatus.Experimental
    Builder registerLazyFixes(@NotNull Consumer<? super QuickFixActionRegistrar> quickFixComputer);

    @ApiStatus.Experimental
    default @NotNull Builder registerFix(@NotNull ModCommandAction action,
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

  public @Nullable ProblemGroup getProblemGroup() {
    return myProblemGroup;
  }

  /**
   * @deprecated Use {@link #newHighlightInfo(HighlightInfoType)} instead.
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public static @NotNull HighlightInfo fromAnnotation(@NotNull Annotation annotation, @NotNull Document document) {
    return fromAnnotation(ExternalAnnotator.class, annotation, false,document);
  }
  /**
   * @deprecated absolutely do not use. quick fixes registered with this Annotation won't be transferred to HighlightInfo.
   * Use {@link #newHighlightInfo(HighlightInfoType)} instead.
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public static @NotNull HighlightInfo fromAnnotation(@NotNull Annotation annotation) {
    return fromAnnotation(ExternalAnnotator.class, annotation, false, new DocumentImpl(""));
  }

  @ApiStatus.Internal
  public static @NotNull HighlightInfo fromAnnotation(@NotNull ExternalAnnotator<?,?> externalAnnotator, @NotNull Annotation annotation, @NotNull Document document) {
    return fromAnnotation(externalAnnotator.getClass(), annotation, false, document);
  }

  static @NotNull HighlightInfo fromAnnotation(@NotNull Class<?> annotatorClass, @NotNull Annotation annotation, boolean batchMode, @NotNull Document document) {
    TextAttributes forcedAttributes = annotation.getEnforcedTextAttributes();
    TextAttributesKey key = annotation.getTextAttributes();
    TextAttributesKey forcedAttributesKey = forcedAttributes == null && key != HighlighterColors.NO_HIGHLIGHTING ? key : null;

    HighlightInfo info = new HighlightInfo(
      forcedAttributes, forcedAttributesKey, convertType(annotation), annotation.getStartOffset(), annotation.getEndOffset(),
      annotation.getMessage(), annotation.getTooltip(), annotation.getSeverity(), annotation.isAfterEndOfLine(),
      annotation.needsUpdateOnTyping(),
      annotation.isFileLevelAnnotation(), 0, annotation.getProblemGroup(), annotatorClass, annotation.getGutterIconRenderer(), HighlightInfoUpdaterImpl.MANAGED_HIGHLIGHT_INFO_GROUP,
      false, annotation.getLazyQuickFixes());

    List<Annotation.QuickFixInfo> fixes = batchMode ? annotation.getBatchFixes() : annotation.getQuickFixes();
    if (fixes != null) {
      List<IntentionActionDescriptor> descriptors = ContainerUtil.map(fixes, af -> {
        TextRange range = af.textRange;
        HighlightDisplayKey k = af.key != null ? af.key : HighlightDisplayKey.find(ANNOTATOR_INSPECTION_SHORT_NAME);
        return new IntentionActionDescriptor(af.quickFix, null, HighlightDisplayKey.getDisplayNameByKey(k), null, k, annotation.getProblemGroup(), info.getSeverity(), range);
      });
      info.registerFixes(descriptors, document);
    }

    return info;
  }

  private static @NotNull HighlightInfoType convertType(@NotNull Annotation annotation) {
    ProblemHighlightType type = annotation.getHighlightType();
    HighlightSeverity severity = annotation.getSeverity();
    return toHighlightInfoType(type, severity);
  }

  private static @NotNull HighlightInfoType toHighlightInfoType(ProblemHighlightType problemHighlightType, @NotNull HighlightSeverity severity) {
    if (problemHighlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) return HighlightInfoType.UNUSED_SYMBOL;
    if (problemHighlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL) return HighlightInfoType.WRONG_REF;
    if (problemHighlightType == ProblemHighlightType.LIKE_DEPRECATED) return HighlightInfoType.DEPRECATED;
    if (problemHighlightType == ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL) return HighlightInfoType.MARKED_FOR_REMOVAL;
    if (problemHighlightType == ProblemHighlightType.POSSIBLE_PROBLEM) return HighlightInfoType.POSSIBLE_PROBLEM;
    return convertSeverity(severity);
  }

  public static @NotNull HighlightInfoType convertSeverity(@NotNull HighlightSeverity severity) {
    //noinspection deprecation
    return severity == HighlightSeverity.ERROR ? HighlightInfoType.ERROR :
           severity == HighlightSeverity.WARNING ? HighlightInfoType.WARNING :
           severity == HighlightSeverity.INFO ? HighlightInfoType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? HighlightInfoType.WEAK_WARNING :
           severity == HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING ? HighlightInfoType.GENERIC_WARNINGS_OR_ERRORS_FROM_SERVER :
           HighlightInfoType.INFORMATION;
  }

  public static @NotNull ProblemHighlightType convertType(@NotNull HighlightInfoType infoType) {
    if (infoType == HighlightInfoType.ERROR || infoType == HighlightInfoType.WRONG_REF) return ProblemHighlightType.ERROR;
    if (infoType == HighlightInfoType.WARNING) return ProblemHighlightType.WARNING;
    if (infoType == HighlightInfoType.INFORMATION) return ProblemHighlightType.INFORMATION;
    return ProblemHighlightType.WEAK_WARNING;
  }

  public static @NotNull ProblemHighlightType convertSeverityToProblemHighlight(@NotNull HighlightSeverity severity) {
    //noinspection deprecation,removal
    return severity == HighlightSeverity.ERROR ? ProblemHighlightType.ERROR :
           severity == HighlightSeverity.WARNING ? ProblemHighlightType.WARNING :
           severity == HighlightSeverity.INFO ? ProblemHighlightType.INFO :
           severity == HighlightSeverity.WEAK_WARNING ? ProblemHighlightType.WEAK_WARNING : ProblemHighlightType.INFORMATION;
  }

  public boolean hasHint() {
    return isFlagSet(HAS_HINT_MASK);
  }

  private void setHint(boolean hasHint) {
    setFlag(HAS_HINT_MASK, hasHint);
  }

  public int getActualStartOffset() {
    RangeHighlighterEx h = highlighter;
    return h == null || !h.isValid() || isFileLevelAnnotation() ? startOffset : h.getStartOffset();
  }

  public int getActualEndOffset() {
    RangeHighlighterEx h = highlighter;
    return h == null || !h.isValid() || isFileLevelAnnotation() ? endOffset : h.getEndOffset();
  }

  public static final class IntentionActionDescriptor {
    private final IntentionAction myAction;
    private volatile @Unmodifiable List<? extends IntentionAction> myOptions; // null means not initialized yet
    private final @Nullable HighlightDisplayKey myKey;
    private final ProblemGroup myProblemGroup;
    private final HighlightSeverity mySeverity;
    private final @Nls String myDisplayName;
    private final Icon myIcon;
    private Boolean myCanCleanup;
    /**
     * either {@link TextRange} (when the info is just created) or {@link RangeMarker} (when the info is bound to the document)
     * maybe null or empty, in which case it's considered to be equal to the info's range
     */
    private Segment myFixRange;

    /**
     * @deprecated use {@link #IntentionActionDescriptor(IntentionAction, List, String, Icon, HighlightDisplayKey, ProblemGroup, HighlightSeverity, Segment)}
     */
    @Deprecated
    public IntentionActionDescriptor(@NotNull IntentionAction action,
                                     @Nullable @Unmodifiable List<? extends IntentionAction> options,
                                     @Nullable @Nls String displayName,
                                     @Nullable Icon icon,
                                     @Nullable HighlightDisplayKey key,
                                     @Nullable ProblemGroup problemGroup,
                                     @Nullable HighlightSeverity severity) {
      this(action, options, displayName, icon, key, problemGroup, severity, null);
    }

    public IntentionActionDescriptor(@NotNull IntentionAction action,
                                     @Nullable @Unmodifiable List<? extends IntentionAction> options,
                                     @Nullable @Nls String displayName,
                                     @Nullable Icon icon,
                                     @Nullable HighlightDisplayKey key,
                                     @Nullable ProblemGroup problemGroup,
                                     @Nullable HighlightSeverity severity,
                                     @Nullable Segment fixRange) {
      myAction = action;
      myOptions = options;
      myDisplayName = displayName;
      myIcon = icon;
      myKey = key;
      myProblemGroup = problemGroup;
      mySeverity = severity;
      myFixRange = fixRange;
    }

    @Nullable
    @ApiStatus.Internal
    public IntentionActionDescriptor withEmptyAction() {
      if (myKey == null || myKey.getID().equals(ANNOTATOR_INSPECTION_SHORT_NAME)) {
        // No need to show "Inspection 'Annotator' options" quick fix, it wouldn't be actionable.
        return null;
      }

      String displayName = HighlightDisplayKey.getDisplayNameByKey(myKey);
      if (displayName == null) return null;
      return new IntentionActionDescriptor(new EmptyIntentionAction(displayName), myOptions, myDisplayName, myIcon,
                                           myKey, myProblemGroup, mySeverity, myFixRange);
    }
    @NotNull
    IntentionActionDescriptor withProblemGroupAndSeverity(@Nullable ProblemGroup problemGroup, @Nullable HighlightSeverity severity) {
      return new IntentionActionDescriptor(myAction, myOptions, myDisplayName, myIcon, myKey, problemGroup, severity, myFixRange);
    }
    @NotNull
    @ApiStatus.Internal
    IntentionActionDescriptor withFixRange(@NotNull Segment fixRange) {
      return new IntentionActionDescriptor(myAction, myOptions, myDisplayName, myIcon, myKey, myProblemGroup, mySeverity, fixRange);
    }

    public @NotNull IntentionAction getAction() {
      return myAction;
    }

    @ApiStatus.Internal
    public boolean isError() {
      return mySeverity == null || mySeverity.compareTo(HighlightSeverity.ERROR) >= 0;
    }

    @ApiStatus.Internal
    public boolean isInformation() {
      return HighlightSeverity.INFORMATION.equals(mySeverity);
    }

    @ApiStatus.Internal
    public boolean canCleanup(@NotNull PsiElement element) {
      if (myCanCleanup == null) {
        InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
        if (myKey == null) {
          myCanCleanup = false;
        }
        else {
          InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(myKey.getShortName(), element);
          myCanCleanup = toolWrapper != null && toolWrapper.isCleanupTool();
        }
      }
      return myCanCleanup;
    }

    public @NotNull Iterable<? extends IntentionAction> getOptions(@NotNull PsiElement element, @Nullable Editor editor) {
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
      List<IntentionAction> newOptions = new ArrayList<>(intentionManager.getStandardIntentionOptions(key, element));
      InspectionProfile profile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
      InspectionToolWrapper<?, ?> toolWrapper = profile.getInspectionTool(key.getShortName(), element);
      if (toolWrapper != null) {
        myCanCleanup = toolWrapper.isCleanupTool();

        IntentionAction fixAllIntention = intentionManager.createFixAllIntention(toolWrapper, myAction);
        InspectionProfileEntry wrappedTool =
          toolWrapper instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)toolWrapper).getTool()
                                                            : ((GlobalInspectionToolWrapper)toolWrapper).getTool();
        if (ANNOTATOR_INSPECTION_SHORT_NAME.equals(wrappedTool.getShortName())) {
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

    private synchronized @NotNull List<? extends IntentionAction> updateOptions(@NotNull List<? extends IntentionAction> newOptions) {
      List<? extends IntentionAction> options = myOptions;
      if (options == null) {
        myOptions = options = newOptions;
      }
      return options;
    }

    public @Nullable @Nls String getDisplayName() {
      return myDisplayName;
    }

    @Override
    public String toString() {
      String name = getAction().getFamilyName();
      return "IntentionActionDescriptor: '" + name + "' (" + ReportingClassSubstitutor.getClassToReport(getAction()) + ")"
        + (myFixRange == null || myFixRange.getStartOffset() == myFixRange.getEndOffset() ? "" : "; fixRange: "+TextRange.create(myFixRange)+"("+myFixRange.getClass()+")");
    }

    public @Nullable Icon getIcon() {
      return myIcon;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof IntentionActionDescriptor && myAction.equals(((IntentionActionDescriptor)obj).myAction);
    }

    public @Nullable String getToolId() {
      return myKey != null ? myKey.getID() : null;
    }

    /**
     * {@link HighlightInfo#fixRange} of original {@link HighlightInfo}
     * Used to check intention's availability at given offset.
     * Can be null, in which case the fix range is the same as {@link HighlightInfo}'s fix range, which can be retrieved via {@link HighlightInfo#findRegisteredQuickFix}
     */
    @ApiStatus.Internal
    public TextRange getFixRange() {
      Segment range = myFixRange;
      return range instanceof TextRange tr ? tr : range instanceof RangeMarker marker ? marker.getTextRange() : null;
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

  @ApiStatus.Internal
  public int getGroup() {
    return group;
  }

  @ApiStatus.Internal
  public boolean isFromInjection() {
    return isFlagSet(FROM_INJECTION_MASK);
  }

  public @NotNull String getText() {
    if (isFileLevelAnnotation()) return "";
    RangeHighlighterEx highlighter = this.highlighter;
    if (highlighter == null) {
      throw new RuntimeException("info not applied yet");
    }
    TextRange range = highlighter.getTextRange();
    if (!highlighter.isValid()) return "";
    String text = highlighter.getDocument().getText();
    return text.substring(Math.min(range.getStartOffset(), text.length()), Math.min(range.getEndOffset(), text.length()));
  }

  /**
   * @deprecated Use {@link Builder#registerFix(IntentionAction, List, String, TextRange, HighlightDisplayKey)} instead
   * Invoking this method might lead to disappearing/flickering quick fixes, due to inherent data races because of the unrestricted call context.
   */
  @Deprecated
  public
  void registerFix(@NotNull IntentionAction action,
                   @Nullable List<? extends IntentionAction> options,
                   @Nullable @Nls String displayName,
                   @Nullable TextRange fixRange,
                   @Nullable HighlightDisplayKey key) {
    registerFixes(List.of(new IntentionActionDescriptor(action, options, displayName, null, key, myProblemGroup, getSeverity(), fixRange)), null);
  }

  // synchronized to avoid concurrent access to quickFix* fields; TODO rework to lock-free
  @ApiStatus.Internal
  synchronized void registerFixes(@NotNull List<? extends @NotNull IntentionActionDescriptor> fixes, @Nullable Document document) {
    if (fixes.isEmpty()) {
      return;
    }
    List<IntentionActionDescriptor> descriptors = myIntentionActionDescriptors;
    List<IntentionActionDescriptor> result = new ArrayList<>(descriptors.size() + fixes.size());
    result.addAll(descriptors);
    result.addAll(fixes);
    myIntentionActionDescriptors = List.copyOf(result);
    updateFields(getIntentionActionDescriptors(), document);
  }

  /**
   * only for internal usages
   */
  @ApiStatus.Internal
  public synchronized void updateLazyFixesPsiTimeStamp(long psiTimeStamp) {
    List<LazyFixDescription> newFixes = ContainerUtil.map(myLazyQuickFixes,
                                                     d -> d.psiModificationStamp() == 0
                                                          ? new LazyFixDescription(d.fixesComputer(), psiTimeStamp, d.future())
                                                          : d);
    if (!newFixes.equals(myLazyQuickFixes)) {
      myLazyQuickFixes = newFixes;
    }
  }
  // must be called from synchronized(this)
  private void updateFields(@NotNull @Unmodifiable List<? extends IntentionActionDescriptor> descriptors, @Nullable Document document) {
    long newFixRange = TextRangeScalarUtil.toScalarRange(getFixTextRange());
    for (IntentionActionDescriptor descriptor : descriptors) {
      TextRange fixRange = descriptor.getFixRange();
      if (descriptor.myAction instanceof HintAction) {
        setHint(true);
      }
      if (descriptor.myFixRange instanceof RangeMarker marker) {
        document = marker.getDocument();
      }
      if (fixRange != null) {
        newFixRange = TextRangeScalarUtil.union(newFixRange, TextRangeScalarUtil.toScalarRange(fixRange));
      }
    }
    RangeHighlighterEx highlighter = this.highlighter;
    if (document == null) {
      RangeMarker fixMarker = this.fixMarker;
      document = fixMarker != null && fixMarker != FIX_MARKER_SAME_AS_HIGHLIGHTER ? fixMarker.getDocument() : highlighter != null ? highlighter.getDocument() : null;
    }
    if (document != null) {
      newFixRange = TextRangeScalarUtil.coerce(newFixRange, 0, document.getTextLength());

      // highlighter already has been created, we need to update IntentionActionDescriptor.myFixRange
      Long2ObjectMap<RangeMarker> cache = getRangeMarkerCache();
      updateDescriptorFixRanges(descriptors, document, cache, newFixRange);
      if (highlighter != null && highlighter.isValid()) {
        long highlighterRange = TextRangeScalarUtil.toScalarRange(highlighter);
        updateFixMarker(document, cache, newFixRange, highlighterRange);
      }
    }
  }

  private @NotNull Long2ObjectMap<RangeMarker> getRangeMarkerCache() {
    Long2ObjectMap<RangeMarker> cache = new Long2ObjectOpenHashMap<>();
    for (IntentionActionDescriptor pair : getIntentionActionDescriptors()) {
      Segment fixRange = pair.myFixRange;
      if (fixRange instanceof RangeMarker marker && marker.isValid()) {
        cache.put(TextRangeScalarUtil.toScalarRange(marker), marker);
        break;
      }
    }
    RangeHighlighterEx highlighter = this.highlighter;
    if (highlighter != null && highlighter.isValid()) {
      cache.putIfAbsent(TextRangeScalarUtil.toScalarRange(highlighter), highlighter);
    }
    return cache;
  }

  public synchronized //TODO rework to lock-free
  void unregisterQuickFix(@NotNull Condition<? super IntentionAction> condition) {
    myIntentionActionDescriptors = List.copyOf(ContainerUtil.filter(myIntentionActionDescriptors, descriptor -> !condition.value(descriptor.getAction())));
  }

  public synchronized IntentionAction getSameFamilyFix(@NotNull IntentionActionWithFixAllOption action) {
    for (IntentionActionDescriptor descriptor : getIntentionActionDescriptors()) {
      IntentionAction other = IntentionActionDelegate.unwrap(descriptor.getAction());
      if (other instanceof IntentionActionWithFixAllOption &&
          action.belongsToMyFamily((IntentionActionWithFixAllOption)other)) {
        return other;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public boolean containsOffset(int offset, boolean includeFixRange) {
    RangeHighlighterEx highlighter = getHighlighter();
    if (highlighter == null || !highlighter.isValid()) return false;
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (startOffset <= offset && offset <= endOffset) {
      return true;
    }
    if (!includeFixRange) return false;
    Segment fixRange = getFixTextRange();
    startOffset = fixRange.getStartOffset();
    endOffset = fixRange.getEndOffset();
    return startOffset <= offset && offset <= endOffset;
  }
  private static @NotNull RangeMarker getOrCreate(@NotNull Document document,
                                                  @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                                  long textRange) {
    return range2markerCache.computeIfAbsent(textRange, __ -> document.createRangeMarker(TextRangeScalarUtil.startOffset(textRange),
                                                                                         TextRangeScalarUtil.endOffset(textRange)));
  }

  /**
   * convert ranges to markers:
   *  - {@link IntentionActionDescriptor#myFixRange} from {@link TextRange} to {@link RangeMarker}, and
   *  - {@link #fixRange} -> {@link #fixMarker}
   * TODO rework to lock-free
   */
  synchronized void updateQuickFixFields(@NotNull Document document,
                                         @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                         long finalHighlighterRange) {
    long fixTextRange = TextRangeScalarUtil.coerce(TextRangeScalarUtil.toScalarRange(getFixTextRange()), 0, document.getTextLength());
    updateFixMarker(document, range2markerCache, fixTextRange, finalHighlighterRange);
    updateDescriptorFixRanges(getIntentionActionDescriptors(), document, range2markerCache, fixTextRange);
  }

  // must be called from synchronized(this)
  private static void updateDescriptorFixRanges(@NotNull List<? extends IntentionActionDescriptor> descriptors,
                                                @NotNull Document document,
                                                @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                                long fixTextRange) {
    for (IntentionActionDescriptor descriptor : descriptors) {
      Segment descriptorFixRange = descriptor.myFixRange;
      if (descriptorFixRange instanceof TextRange tr) {
        descriptor.myFixRange = getOrCreate(document, range2markerCache, TextRangeScalarUtil.toScalarRange(tr));
      }
      else if (descriptorFixRange == null) {
        descriptor.myFixRange = getOrCreate(document, range2markerCache, fixTextRange);
      }
    }
  }

  private synchronized void updateFixMarker(@NotNull Document document,
                                            @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                            long newFixRange,
                                            long finalHighlighterRange) {
    if (newFixRange == finalHighlighterRange) {
      fixMarker = FIX_MARKER_SAME_AS_HIGHLIGHTER;
    }
    else {
      fixMarker = getOrCreate(document, range2markerCache, newFixRange);
    }
  }

  /**
   * true if {@link #myLazyQuickFixes} contains deferred computations that are not yet completed.
   * For example, when this HighlightInfo was created as an error for some unresolved reference, and some "Import" quickfixes are to be computed, after {@link UnresolvedReferenceQuickFixProvider} asked about em
   */
  @ApiStatus.Internal
  public synchronized boolean hasLazyQuickFixes() {
    return !myLazyQuickFixes.isEmpty();
  }

  @ApiStatus.Internal
  public boolean isFromAnnotator() {
    return HighlightInfoUpdaterImpl.isAnnotatorToolId(toolId);
  }

  @ApiStatus.Internal
  public boolean isFromInspection() {
    return HighlightInfoUpdaterImpl.isInspectionToolId(toolId);
  }

  @ApiStatus.Internal
  public boolean isFromHighlightVisitor() {
    return HighlightInfoUpdaterImpl.isHighlightVisitorToolId(toolId);
  }
  @ApiStatus.Internal
  boolean isInjectionRelated() {
    return HighlightInfoUpdaterImpl.isInjectionRelated(toolId);
  }

  @ApiStatus.Internal
  public static @NotNull HighlightInfo createComposite(@NotNull List<? extends HighlightInfo> infos) {
    // derive composite's offsets from an info with tooltip, if present
    HighlightInfo anchorInfo = ContainerUtil.find(infos, info -> info.getToolTip() != null);
    if (anchorInfo == null) anchorInfo = infos.get(0);
    HighlightInfo info = new HighlightInfo(null, null, anchorInfo.type, anchorInfo.startOffset, anchorInfo.endOffset,
                                           createCompositeDescription(infos), createCompositeTooltip(infos),
                                           anchorInfo.type.getSeverity(null), false, null, false, 0,
                                           anchorInfo.getProblemGroup(), null, anchorInfo.getGutterIconRenderer(), anchorInfo.getGroup(),
                                           anchorInfo.hasHint(), anchorInfo.getLazyQuickFixes());
    synchronized (info) {
      info.highlighter = anchorInfo.getHighlighter();
      info.myIntentionActionDescriptors = ContainerUtil.concat(ContainerUtil.map(infos, i-> ((HighlightInfo)i).myIntentionActionDescriptors));
    }
    return info;
  }
  private static @Nullable @NlsSafe String createCompositeDescription(@NotNull List<? extends HighlightInfo> infos) {
    StringBuilder description = new StringBuilder();
    boolean isNull = true;
    for (HighlightInfo info : infos) {
      String itemDescription = info.getDescription();
      if (itemDescription != null) {
        itemDescription = itemDescription.trim();
        description.append(itemDescription);
        if (!itemDescription.endsWith(".")) {
          description.append('.');
        }
        description.append(' ');

        isNull = false;
      }
    }
    return isNull ? null : description.toString();
  }

  private static @Nullable @NlsSafe String createCompositeTooltip(@NotNull List<? extends HighlightInfo> infos) {
    StringBuilder result = new StringBuilder();
    for (HighlightInfo info : infos) {
      String toolTip = info.getToolTip();
      if (toolTip != null) {
        if (!result.isEmpty()) {
          //noinspection SpellCheckingInspection
          result.append("<hr size=1 noshade>");
        }
        toolTip = XmlStringUtil.stripHtml(toolTip);
        result.append(toolTip);
      }
    }
    if (result.isEmpty()) {
      return null;
    }
    return XmlStringUtil.wrapInHtml(result);
  }

  void computeQuickFixesSynchronously(@NotNull PsiFile psiFile, @NotNull Document document) throws ExecutionException, InterruptedException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<LazyFixDescription> pairs;
    synchronized (this) {
      pairs = new ArrayList<>(myLazyQuickFixes);
    }
    List<LazyFixDescription> newPairs = ContainerUtil.map(pairs, desc -> {
      Future<? extends List<IntentionActionDescriptor>> future = desc.future();
      if (future == null) {
        Consumer<? super QuickFixActionRegistrar> computer = desc.fixesComputer();
        future = CompletableFuture.completedFuture(doComputeLazyQuickFixes(document, psiFile.getProject(), desc.psiModificationStamp(), computer));
        return new LazyFixDescription(computer, desc.psiModificationStamp(), future);
      }
      else {
        return desc;
      }
    });
    for (LazyFixDescription newPair : newPairs) {
      newPair.future().get();
    }
    synchronized (this) {
      if (!newPairs.equals(pairs)) {
        myLazyQuickFixes = newPairs;
      }
    }
  }

  @NotNull
  private List<IntentionActionDescriptor> doComputeLazyQuickFixes(@NotNull Document document,
                                                                  @NotNull Project project,
                                                                  long oldPsiModificationStamp,
                                                                  @NotNull Consumer<? super QuickFixActionRegistrar> computation) {
    if (project.isDisposed()
        || PsiDocumentManager.getInstance(project).isUncommited(document)
        || PsiManager.getInstance(project).getModificationTracker().getModificationCount() != oldPsiModificationStamp
    ) {
      return List.of();
    }
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors());
    List<IntentionActionDescriptor> newDescriptors = Collections.synchronizedList(new ArrayList<>());
    QuickFixActionRegistrar registrarDelegate = new QuickFixActionRegistrar() {
      @Override
      public void register(@NotNull IntentionAction action) {
        doRegister(getFixTextRange(), action, null);
      }

      @Override
      public void register(@NotNull TextRange fixRange, @NotNull IntentionAction action, @Nullable HighlightDisplayKey key) {
        doRegister(fixRange, action, key);
      }
      private void doRegister(@NotNull Segment fixRange, @NotNull IntentionAction action, @Nullable HighlightDisplayKey key) {
        IntentionActionDescriptor descriptor = new IntentionActionDescriptor(action, null, null, null, key, myProblemGroup, severity, fixRange);
        newDescriptors.add(descriptor);
        synchronized (HighlightInfo.this) {
          updateFields(List.of(descriptor), document);
          assertIntentionActionDescriptorsAreRangeMarkerBased(List.of(descriptor));
        }
      }
    };
    computation.accept(registrarDelegate);
    assertIntentionActionDescriptorsAreRangeMarkerBased(newDescriptors);
    return newDescriptors;
  }

  private static void assertIntentionActionDescriptorsAreRangeMarkerBased(@NotNull List<? extends IntentionActionDescriptor> descriptors) {
    for (IntentionActionDescriptor descriptor : descriptors) {
      assert descriptor.myFixRange  == null || descriptor.myFixRange instanceof RangeMarker : descriptor +"; descriptors:"+descriptors;
    }
  }

  /**
   * Starts computing lazy quick fixes in the background.
   * The result will be stored back in {@link #myLazyQuickFixes} inside {@link LazyFixDescription#future}
   */
  synchronized void startComputeQuickFixes(@NotNull Document document, @NotNull Project project) {
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors());
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<LazyFixDescription> newPairs = ContainerUtil.map(myLazyQuickFixes, description -> {
      Future<? extends List<IntentionActionDescriptor>> future = description.future();
      if (future == null) {
        Consumer<? super QuickFixActionRegistrar> computer = description.fixesComputer();
        future = ReadAction.nonBlocking(()->{
          AtomicReference<List<IntentionActionDescriptor>> result = new AtomicReference<>(List.of());
          ((ApplicationEx)ApplicationManager.getApplication()).executeByImpatientReader(
            () -> result.set(doComputeLazyQuickFixes(document, project, description.psiModificationStamp, computer)));
          return result.get();
        }).submit(ForkJoinPool.commonPool());
        return new LazyFixDescription(computer, PsiManager.getInstance(project).getModificationTracker().getModificationCount(), future);
      }
      return description;
    });
    if (!newPairs.equals(myLazyQuickFixes)) {
      myLazyQuickFixes = newPairs;
    }
  }

  void copyComputedLazyFixesTo(@NotNull HighlightInfo newInfo, @NotNull Document document) {
    List<LazyFixDescription> list;
    synchronized (this) {
      list = new ArrayList<>(myLazyQuickFixes);
    }
    synchronized (newInfo) {
      if (newInfo.myLazyQuickFixes.size() == list.size() && psiModificationStampIsTheSame(newInfo.myLazyQuickFixes, list)) {
        newInfo.myLazyQuickFixes = list;
        newInfo.updateFields(newInfo.getIntentionActionDescriptors(), document);
      }
    }
  }

  private static boolean psiModificationStampIsTheSame(@NotNull @Unmodifiable List<LazyFixDescription> list1,
                                                       @NotNull @Unmodifiable List<LazyFixDescription> list2) {
    for (int i = 0; i < list1.size(); i++) {
      LazyFixDescription fix1 = list1.get(i);
      LazyFixDescription fix2 = list2.get(i);
      if (fix1.psiModificationStamp() != fix2.psiModificationStamp()) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Unmodifiable
  synchronized List<? extends @NotNull Consumer<? super QuickFixActionRegistrar>>
  getLazyQuickFixes() {
    return ContainerUtil.map(myLazyQuickFixes, p -> p.fixesComputer());
  }

  @NotNull
  HighlightInfo copy() {
    HighlightInfo info =
      new HighlightInfo(forcedTextAttributes, forcedTextAttributesKey, type, startOffset, endOffset, description, toolTip, severity,
                        isAfterEndOfLine(), needUpdateOnTyping(), isFileLevelAnnotation(), navigationShift, myProblemGroup, toolId,
                        gutterIconRenderer, group, hasHint(), List.of());
    synchronized (info) {
      info.myIntentionActionDescriptors = myIntentionActionDescriptors;
      info.myLazyQuickFixes = myLazyQuickFixes;
      info.myFlags = myFlags;
      info.fixMarker = fixMarker;
    }
    return info;
  }
}
