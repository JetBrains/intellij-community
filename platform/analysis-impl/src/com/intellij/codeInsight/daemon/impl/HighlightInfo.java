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
import com.intellij.openapi.progress.ProgressIndicator;
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
  static final String ANNOTATOR_INSPECTION_SHORT_NAME = "Annotator";
  // optimization: if tooltip contains this marker object, then it replaced with description field in getTooltip()
  private static final String DESCRIPTION_PLACEHOLDER = "\u0000";

  private static final byte FROM_INJECTION_MASK = 0x1;
  private static final byte AFTER_END_OF_LINE_MASK = 0x2;
  private static final byte FILE_LEVEL_ANNOTATION_MASK = 0x4;

  @MagicConstant(intValues = {FROM_INJECTION_MASK, AFTER_END_OF_LINE_MASK, FILE_LEVEL_ANNOTATION_MASK})
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

  private record LazyFixDescription(
    // the computation which is (or will be) running in the #future in BGT
    @NotNull Consumer<? super QuickFixActionRegistrar> fixesComputer,
    // 0 means the stamp not set yet
    long psiModificationStamp,
    // list of (code fragment to be executed in BGT, and their execution result future)
    // future == null means the code was never executed, not-null means the execution has started, .isDone() means it's completed
    @Nullable Future<@NotNull List<IntentionActionDescriptor>> future,
    // the ProgressIndicator under which the future is running
    @NotNull ProgressIndicator progressIndicator) {}

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
   * During the creation of this {@link HighlightInfo} the fix range is accumulated here, and then moved to {@link OffsetStore##fixMarker},
   * when this {@link HighlightInfo} is associated with {@link Document}, to support updating its fix range along with the document changes.
   * After this point {@link OffsetStore#fixMarker} stores the actual fix range, and {@code fixRange} is not needed anymore.
   */
  private final long fixRange;
  /**
   * @see FlagConstant for allowed values
   */
  private volatile byte myFlags;

  @ApiStatus.Internal
  public final int navigationShift;

  private @Nullable Object fileLevelComponentsStorage;

  // a bunch of offset-related objects (like RangeMarkers/IntentionActionDescriptors) that are stored in a separate object for atomicity
  private record OffsetStore(
    @Nullable RangeHighlighterEx highlighter,
    @Nullable("null means its fix range is the same as the range of #highlighter") RangeMarker fixMarker,
    @NotNull @Unmodifiable List<? extends IntentionActionDescriptor> intentionActionDescriptors,
    @NotNull @Unmodifiable List<? extends LazyFixDescription> lazyQuickFixes
  ) {
    @NotNull OffsetStore withLazyQuickFixes(@NotNull @Unmodifiable List<? extends LazyFixDescription> newLazyQuickFixes) {
      return new OffsetStore(highlighter(), fixMarker(), intentionActionDescriptors(), newLazyQuickFixes);
    }
    @NotNull OffsetStore withIntentionDescriptorsAndFixMarker(@NotNull @Unmodifiable List<? extends IntentionActionDescriptor> newIntentionDescriptors, @Nullable RangeMarker fixMarker) {
      return new OffsetStore(highlighter(), fixMarker, newIntentionDescriptors, lazyQuickFixes());
    }
    @NotNull OffsetStore withHighlighter(@NotNull RangeHighlighterEx highlighter) {
      return new OffsetStore(highlighter, fixMarker(), intentionActionDescriptors(), lazyQuickFixes());
    }
  }
  // store some offset-containing things in a separate record for atomicity and lock-freedom
  @NotNull
  private volatile OffsetStore offsetStore;
  private static final VarHandle OFFSET_STORE_HANDLE;
  static {
    try {
      OFFSET_STORE_HANDLE = MethodHandles
        .privateLookupIn(HighlightInfo.class, MethodHandles.lookup())
        .findVarHandle(HighlightInfo.class, "offsetStore", OffsetStore.class);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
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
                          boolean isFileLevelAnnotation,
                          int navigationShift,
                          @Nullable ProblemGroup problemGroup,
                          @Nullable Object toolId,
                          @Nullable GutterMark gutterIconRenderer,
                          int group,
                          @NotNull @Unmodifiable List<? extends @NotNull Consumer<? super QuickFixActionRegistrar>> lazyFixes) {
    if (startOffset < 0 || startOffset > endOffset) {
      throw new IllegalArgumentException("Incorrect highlightInfo bounds: startOffset="+startOffset+"; endOffset="+endOffset+";type="+type+"; description="+escapedDescription+". Maybe you forgot to call .range()?");
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
                     (isFileLevelAnnotation ? FILE_LEVEL_ANNOTATION_MASK : 0)
    );
    this.navigationShift = navigationShift;
    myProblemGroup = problemGroup;
    this.gutterIconRenderer = gutterIconRenderer;
    this.toolId = toolId;
    this.group = group;
    List<LazyFixDescription> myLazyQuickFixes =
      ContainerUtil.map(lazyFixes, c -> new LazyFixDescription(c, 0, null, new DaemonProgressIndicator()));
    offsetStore = new OffsetStore(null, null, List.of(), myLazyQuickFixes);
  }

  @ApiStatus.Internal
  public void setToolId(Object toolId) {
    this.toolId = toolId;
  }

  @ApiStatus.Internal
  public Object getToolId() {
    return toolId;
  }

  @NotNull
  @Unmodifiable
  private static List<IntentionActionDescriptor> getIntentionActionDescriptors(@NotNull OffsetStore store) {
    return ContainerUtil.concat(store.intentionActionDescriptors(), ContainerUtil.flatMap(store.lazyQuickFixes(), desc-> {
      Future<@NotNull List<IntentionActionDescriptor>> future = desc.future();
      if (future != null && future.isDone()) {
        try {
          List<IntentionActionDescriptor> coll = future.get();
          assert coll != null : future +"; "+future.getClass()+"; desc="+desc;
          return List.copyOf(coll);
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
  /**
   * Find the quickfix (among ones added by {@link #registerFixes}) selected by returning non-null value from the {@code predicate}
   * and return that value, or null if the quickfix was not found.
   * @param predicate called with the found {@link IntentionActionDescriptor}, and its fix range, and returns a value.
   */
  public <T> T findRegisteredQuickFix(@NotNull BiFunction<? super @NotNull IntentionActionDescriptor, ? super @NotNull TextRange, ? extends @Nullable T> predicate) {
    OffsetStore store = offsetStore;
    List<IntentionActionDescriptor> descriptors = getIntentionActionDescriptors(store);
    Set<IntentionActionDescriptor> processed = new HashSet<>();
    for (IntentionActionDescriptor descriptor : descriptors) {
      if (!processed.add(descriptor)) continue;
      TextRange fixRange = descriptor.getFixRange();
      if (fixRange == null) {
        fixRange = TextRangeScalarUtil.create(getFixTextRangeScalar(store));
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
  private Segment getFixTextRange() {
    OffsetStore store = offsetStore;
    RangeMarker myFixMarker = store.fixMarker();
    if (myFixMarker == null) {
      RangeHighlighterEx myHighlighter = store.highlighter();
      if (myHighlighter != null && myHighlighter.isValid()) {
        return myHighlighter;
      }
    }
    else if (myFixMarker.isValid()) {
      return myFixMarker;
    }
    return TextRangeScalarUtil.create(fixRange);
  }
  private long getFixTextRangeScalar(@NotNull OffsetStore store) {
    RangeMarker myFixMarker = store.fixMarker();
    RangeHighlighterEx myHighlighter = store.highlighter();
    if (myFixMarker == null) {
      if (myHighlighter != null && myHighlighter.isValid()) {
        return TextRangeScalarUtil.toScalarRange(myHighlighter);
      }
    }
    else if (myFixMarker.isValid()) {
      return TextRangeScalarUtil.toScalarRange(myFixMarker);
    }
    return fixRange;
  }

  @ApiStatus.Internal
  public void markFromInjection() {
    setFlag(FROM_INJECTION_MASK);
  }

  @ApiStatus.Internal
  public void addFileLevelComponent(@NotNull FileEditor fileEditor, @NotNull JComponent component) {
    if (fileLevelComponentsStorage == null) {
      fileLevelComponentsStorage = new Pair<>(fileEditor, component);
    }
    else if (fileLevelComponentsStorage instanceof Pair<?,?> p) {
      //noinspection unchecked
      Pair<FileEditor, JComponent> pair = (Pair<FileEditor, JComponent>)p;
      Map<FileEditor, JComponent> map = new HashMap<>();
      map.put(pair.first, pair.second);
      map.put(fileEditor, component);
      fileLevelComponentsStorage = map;
    }
    else if (fileLevelComponentsStorage instanceof Map<?,?> map) {
      //noinspection unchecked
      ((Map<FileEditor, JComponent>)map).put(fileEditor, component);
    }
    else {
      LOG.error(new IllegalStateException("fileLevelComponents=" + fileLevelComponentsStorage));
        }
  }

  @ApiStatus.Internal
  public void removeFileLeverComponent(@NotNull FileEditor fileEditor) {
    if (fileLevelComponentsStorage instanceof Pair<?,?> p) {
      //noinspection unchecked
      Pair<FileEditor, JComponent> pair = (Pair<FileEditor, JComponent>)p;
      if (pair.first == fileEditor) {
        fileLevelComponentsStorage = null;
      }
    }
    else if (fileLevelComponentsStorage instanceof Map<?,?> map) {
      //noinspection unchecked
      ((Map<FileEditor, JComponent>)map).remove(fileEditor);
    }
  }

  @ApiStatus.Internal
  public @Nullable JComponent getFileLevelComponent(@NotNull FileEditor fileEditor) {
    if (fileLevelComponentsStorage == null) {
      return null;
    }
    else if (fileLevelComponentsStorage instanceof Pair<?,?> p) {
      //noinspection unchecked
      Pair<FileEditor, JComponent> pair = (Pair<FileEditor, JComponent>)p;
      return pair.first == fileEditor ? pair.second : null;
    }
    else if (fileLevelComponentsStorage instanceof Map<?,?> map) {
      //noinspection unchecked
      return ((Map<FileEditor, JComponent>)map).get(fileEditor);
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
    return myProblemGroup instanceof ExternalSourceProblemGroup externalSourceId ?
           externalSourceId.getExternalCheckName() : null;
  }

  private boolean isFlagSet(@FlagConstant byte mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  private void setFlag(@FlagConstant byte mask) {
    //noinspection NonAtomicOperationOnVolatileField
    myFlags = BitUtil.set(myFlags, mask, true);
  }

  @ApiStatus.Internal
  public boolean isFileLevelAnnotation() {
    return isFlagSet(FILE_LEVEL_ANNOTATION_MASK);
  }

  public @NotNull HighlightSeverity getSeverity() {
    return severity;
  }

  public RangeHighlighterEx getHighlighter() {
    return offsetStore.highlighter();
  }

  public void setHighlighter(@NotNull RangeHighlighterEx highlighter) {
    update(oldStore -> {
      if (oldStore.highlighter() != null) {
        throw new IllegalStateException("Cannot set highlighter to " + highlighter + " because it already set: " +
                                        oldStore.highlighter() + ". Maybe this HighlightInfo was (incorrectly) stored and reused?");
      }
      OffsetStore newFixes = oldStore.withHighlighter(highlighter);
      // as soon as the HighlightInfo is bound to the document, we can replace TextRanges in IntentionActionDescriptor with RangeMarkers
      return updateFields(newFixes, highlighter.getDocument());
    });
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors(offsetStore));
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
    OffsetStore store = offsetStore;
    RangeHighlighterEx highlighter = store.highlighter();
    if (highlighter != null) {
      s += "; text='" + StringUtil.first(getText(), 40, true) + "'";
      if (!highlighter.isValid()) {
        s += "; highlighter: " + TextRange.create(highlighter) + " is invalid";
      }
    }
    if (getDescription() != null) {
      s += ", description='" + getDescription() + "'";
    }
    s += "; severity=" + getSeverity();
    List<IntentionActionDescriptor> descriptors = getIntentionActionDescriptors(store);
    if (!descriptors.isEmpty()) {
      s += "; quickFixes: " + StringUtil.join(descriptors, ", ");
    }
    if (gutterIconRenderer != null) {
      s += "; gutter: " + gutterIconRenderer;
    }
    if (toolId != null) {
      s += showFullQualifiedClassNames ? "; toolId: " + toolId + " (" + toolId.getClass() + ")" :
           "; toolId: " + (toolId instanceof Class<?> c ? c.getSimpleName() : "not specified");
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
    return new HighlightInfoB(type, false);
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

    /**
     * @deprecated Does nothing
     */
    @Deprecated(forRemoval = true)
    @NotNull Builder needsUpdateOnTyping(boolean update);

    @NotNull Builder severity(@NotNull HighlightSeverity severity);

    @NotNull Builder fileLevelAnnotation();

    /**
     * @param navigationShift the navigation shift relative to the reported error range start offset.
     *                        When navigating to the error, the caret could be shifted automatically
     *                        the specified number of characters to the right.
     * @return this builder
     */
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
      annotation.isFileLevelAnnotation(), 0, annotation.getProblemGroup(), annotatorClass, annotation.getGutterIconRenderer(), HighlightInfoUpdaterImpl.MANAGED_HIGHLIGHT_INFO_GROUP,
      annotation.getLazyQuickFixes());

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
    return ContainerUtil.exists(getIntentionActionDescriptors(offsetStore), descriptor -> descriptor.myAction instanceof HintAction);
  }

  public int getActualStartOffset() {
    RangeHighlighterEx h = offsetStore.highlighter();
    return h == null || !h.isValid() || isFileLevelAnnotation() ? startOffset : h.getStartOffset();
  }

  public int getActualEndOffset() {
    RangeHighlighterEx h = offsetStore.highlighter();
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
    private final Segment myFixRange;

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

    @NotNull
    @ApiStatus.Internal
    IntentionActionDescriptor withRangeMarkerFixRange(@NotNull Document document,
                                                      @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                                      long fallBackFixTextRange) {
      return myFixRange instanceof RangeMarker ? this :
             withFixRange(getOrCreate(document, range2markerCache, myFixRange instanceof TextRange tr ? TextRangeScalarUtil.toScalarRange(tr) : fallBackFixTextRange));
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
          toolWrapper instanceof LocalInspectionToolWrapper local ? local.getTool()
                                                            : ((GlobalInspectionToolWrapper)toolWrapper).getTool();
        if (ANNOTATOR_INSPECTION_SHORT_NAME.equals(wrappedTool.getShortName())) {
          List<IntentionAction> actions = Collections.emptyList();
          if (myProblemGroup instanceof SuppressableProblemGroup suppressible) {
            actions = Arrays.asList(suppressible.getSuppressActions(element));
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
        if (wrappedTool instanceof CustomSuppressableInspectionTool custom) {
          IntentionAction[] suppressActions = custom.getSuppressActions(element);
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
      if (myProblemGroup instanceof SuppressableProblemGroup suppressible) {
        IntentionAction[] suppressActions = suppressible.getSuppressActions(element);
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
      return obj instanceof IntentionActionDescriptor descriptor && myAction.equals(descriptor.myAction);
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
    RangeHighlighterEx highlighter = offsetStore.highlighter();
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

  @ApiStatus.Internal
  void registerFixes(@NotNull List<? extends @NotNull IntentionActionDescriptor> fixes, @Nullable Document document) {
    if (fixes.isEmpty()) {
      return;
    }
    update(oldStore -> {
      List<IntentionActionDescriptor> newDescriptors = List.copyOf(ContainerUtil.concat(oldStore.intentionActionDescriptors(), fixes));
      OffsetStore newStore = oldStore.withIntentionDescriptorsAndFixMarker(newDescriptors, oldStore.fixMarker());
      return updateFields(newStore, document);
    });
  }

  /**
   * only for internal usages
   */
  @ApiStatus.Internal
  public void updateLazyFixesPsiTimeStamp(long psiTimeStamp) {
    update(store -> store.withLazyQuickFixes(ContainerUtil.map(store.lazyQuickFixes(),
                                                         d -> d.psiModificationStamp() == 0
                                                              ? new LazyFixDescription(d.fixesComputer(), psiTimeStamp, d.future(), d.progressIndicator())
                                                              : d)));
  }

  @Contract(pure = true)
  private @NotNull OffsetStore updateFields(@NotNull OffsetStore oldStore, @Nullable Document document) {
    long newFixRange = getFixTextRangeScalar(oldStore);
    for (IntentionActionDescriptor descriptor : oldStore.intentionActionDescriptors()) {
      TextRange descriptorFixRange = descriptor.getFixRange();
      if (document == null && descriptor.myFixRange instanceof RangeMarker marker) {
        document = marker.getDocument();
      }
      if (descriptorFixRange != null) {
        newFixRange = TextRangeScalarUtil.union(newFixRange, TextRangeScalarUtil.toScalarRange(descriptorFixRange));
      }
    }
    RangeHighlighterEx highlighter = oldStore.highlighter();
    if (document == null) {
      RangeMarker fixMarker = oldStore.fixMarker();
      document = fixMarker != null ? fixMarker.getDocument() : highlighter != null ? highlighter.getDocument() : null;
    }
    List<? extends IntentionActionDescriptor> newDescriptors;
    RangeMarker newFixMarker;
    if (document == null) {
      newDescriptors = oldStore.intentionActionDescriptors();
      newFixMarker = null;
    }
    else {
      newFixRange = TextRangeScalarUtil.coerceRange(newFixRange, 0, document.getTextLength());
      Long2ObjectMap<RangeMarker> cache = getRangeMarkerCache(oldStore);
      newDescriptors = toRangeMarkerFixRanges(oldStore.intentionActionDescriptors(), document, cache, newFixRange);
      long highlighterRange = highlighter != null && highlighter.isValid() ? TextRangeScalarUtil.toScalarRange(highlighter) : newFixRange;
      newFixMarker = updateFixMarker(document, cache, newFixRange, highlighterRange);
    }
    return oldStore.withIntentionDescriptorsAndFixMarker(newDescriptors, newFixMarker);
  }

  @Contract(pure = true)
  private static @NotNull Long2ObjectMap<RangeMarker> getRangeMarkerCache(@NotNull OffsetStore store) {
    Long2ObjectMap<RangeMarker> cache = new Long2ObjectOpenHashMap<>();
    for (IntentionActionDescriptor pair : getIntentionActionDescriptors(store)) {
      Segment fixRange = pair.myFixRange;
      if (fixRange instanceof RangeMarker marker && marker.isValid()) {
        cache.put(TextRangeScalarUtil.toScalarRange(marker), marker);
        break;
      }
    }
    RangeHighlighterEx highlighter = store.highlighter();
    if (highlighter != null && highlighter.isValid()) {
      cache.putIfAbsent(TextRangeScalarUtil.toScalarRange(highlighter), highlighter);
    }
    return cache;
  }

  public void unregisterQuickFix(@NotNull Condition<? super IntentionAction> condition) {
    update(oldStore -> oldStore.withIntentionDescriptorsAndFixMarker(List.copyOf(ContainerUtil.filter(oldStore.intentionActionDescriptors(), descriptor -> !condition.value(descriptor.getAction()))), oldStore.fixMarker()));
  }

  public IntentionAction getSameFamilyFix(@NotNull IntentionActionWithFixAllOption action) {
    for (IntentionActionDescriptor descriptor : getIntentionActionDescriptors(offsetStore)) {
      IntentionAction other = IntentionActionDelegate.unwrap(descriptor.getAction());
      if (other instanceof IntentionActionWithFixAllOption option && action.belongsToMyFamily(option)) {
        return other;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  @Contract(pure = true)
  public boolean containsOffset(int offset, boolean includeFixRange) {
    OffsetStore store = offsetStore;
    RangeHighlighterEx highlighter = store.highlighter();
    if (highlighter == null || !highlighter.isValid()) return false;
    int startOffset = highlighter.getStartOffset();
    int endOffset = highlighter.getEndOffset();
    if (startOffset <= offset && offset <= endOffset) {
      return true;
    }
    if (!includeFixRange) return false;
    long fixRange = getFixTextRangeScalar(store);
    return TextRangeScalarUtil.containsOffset(fixRange, offset);
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
   *  - {@link #fixRange} -> {@link OffsetStore#fixMarker}
   * TODO rework to lock-free
   */
  void updateQuickFixFields(@NotNull Document document,
                            @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                            long finalHighlighterRange) {
    update(oldStore -> {
      long fixTextRange = TextRangeScalarUtil.coerceRange(getFixTextRangeScalar(oldStore), 0, document.getTextLength());
      RangeMarker newFixMarker = updateFixMarker(document, range2markerCache, fixTextRange, finalHighlighterRange);
      List<? extends IntentionActionDescriptor> newDescriptors = toRangeMarkerFixRanges(getIntentionActionDescriptors(oldStore), document, range2markerCache, fixTextRange);
      return oldStore.withIntentionDescriptorsAndFixMarker(newDescriptors, newFixMarker);
    });
  }

  @Contract(pure = true)
  private static @NotNull @Unmodifiable List<? extends IntentionActionDescriptor> toRangeMarkerFixRanges(@NotNull List<? extends IntentionActionDescriptor> descriptors,
                                                                                                         @NotNull Document document,
                                                                                                         @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                                                                                         long fixTextRange) {
    return ContainerUtil.map(descriptors, descriptor -> descriptor.withRangeMarkerFixRange(document, range2markerCache, fixTextRange));
  }

  @Contract(pure = true)
  private static RangeMarker updateFixMarker(@NotNull Document document,
                                             @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                             long newFixRange,
                                             long finalHighlighterRange) {
    if (newFixRange == finalHighlighterRange) {
      return null;
    }
    else {
      return getOrCreate(document, range2markerCache, newFixRange);
    }
  }

  /**
   * true if {@link OffsetStore#lazyQuickFixes} contains deferred computations that are not yet completed.
   * For example, when this HighlightInfo was created as an error for some unresolved reference, and some "Import" quickfixes are to be computed, after {@link UnresolvedReferenceQuickFixProvider} asked about em
   */
  @ApiStatus.Internal
  @Contract(pure = true)
  public boolean hasLazyQuickFixes() {
    return !offsetStore.lazyQuickFixes().isEmpty();
  }

  @ApiStatus.Internal
  @Contract(pure = true)
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
  @Contract(pure = true)
  public static @NotNull HighlightInfo createComposite(@NotNull List<? extends HighlightInfo> infos) {
    // derive composite's offsets from an info with tooltip, if present
    HighlightInfo anchorInfo = ContainerUtil.find(infos, info -> info.getToolTip() != null);
    if (anchorInfo == null) anchorInfo = infos.get(0);
    Builder builder = anchorInfo.copy(false);
    String compositeDescription = createCompositeDescription(infos);
    String compositeTooltip = createCompositeTooltip(infos);
    if (compositeDescription != null) {
      builder.description(compositeDescription);
    }
    if (compositeTooltip != null) {
      builder.escapedToolTip(compositeTooltip);
    }
    HighlightInfo info = builder.createUnconditionally();
    OffsetStore oldStore = info.offsetStore;
    List<? extends IntentionActionDescriptor> newDescriptors =
      ContainerUtil.concat(ContainerUtil.map(infos, i -> ((HighlightInfo)i).offsetStore.intentionActionDescriptors()));
    info.offsetStore = oldStore.withIntentionDescriptorsAndFixMarker(newDescriptors, oldStore.fixMarker()).withHighlighter(anchorInfo.getHighlighter());
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

  private void update(@NotNull Function<? super OffsetStore, ? extends OffsetStore> computation) {
    while (true) {
      OffsetStore oldStore = (OffsetStore)OFFSET_STORE_HANDLE.getVolatile(this);
      OffsetStore newStore = computation.apply(oldStore);
      if (oldStore.fixMarker() == newStore.fixMarker() &&
          oldStore.highlighter() == newStore.highlighter() &&
          oldStore.intentionActionDescriptors() == newStore.intentionActionDescriptors() &&
          oldStore.lazyQuickFixes() == newStore.lazyQuickFixes()) {
        // optimization: it does happen when we try to update with the same value
        break;
      }
      if (OFFSET_STORE_HANDLE.compareAndSet(this, oldStore, newStore)) {
        break;
      }
    }
  }

  void computeQuickFixesSynchronously(@NotNull PsiFile psiFile, @NotNull Document document) throws ExecutionException, InterruptedException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    // store results of computation here to avoid re-computing when the CAS fails, because it can be extremely expensive
    Map<Consumer<? super QuickFixActionRegistrar>, @NotNull List<IntentionActionDescriptor>> computerToResult = new IdentityHashMap<>();
    update(oldStore -> {
      List<? extends LazyFixDescription> newLazies = ContainerUtil.map(oldStore.lazyQuickFixes(), desc -> {
        Future<List<IntentionActionDescriptor>> future = desc.future();
        if (future != null && future.isDone()) {
          return desc;
        }
        Consumer<? super QuickFixActionRegistrar> computer = desc.fixesComputer();
        // recompute only if necessary
        List<IntentionActionDescriptor> result =
          computerToResult.computeIfAbsent(computer,
            __ -> doComputeLazyQuickFixes(document, psiFile.getProject(), desc.psiModificationStamp(), computer));
        assert result != null;
        future = CompletableFuture.completedFuture(result);
        return new LazyFixDescription(desc.fixesComputer(), desc.psiModificationStamp(), future, desc.progressIndicator());
      });
      return oldStore.withLazyQuickFixes(newLazies);
    });
    // cancel the computations still going in background, but only after the new futures were assigned,
    // to avoid data race when the progressIndicator canceled, the computation aborted, the future state is "completed exceptionally", some other process picked this up and confused
    for (LazyFixDescription desc : offsetStore.lazyQuickFixes()) {
      desc.progressIndicator().cancel();
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
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors(offsetStore));
    List<IntentionActionDescriptor> lazyDescriptors = Collections.synchronizedList(new ArrayList<>());
    Long2ObjectMap<RangeMarker> cache = getRangeMarkerCache(offsetStore);
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
        IntentionActionDescriptor newDescriptor = descriptor.withRangeMarkerFixRange(document, cache, HighlightInfo.this.fixRange);
        lazyDescriptors.add(newDescriptor);
        assertIntentionActionDescriptorsAreRangeMarkerBased(List.of(newDescriptor));
      }
    };
    computation.accept(registrarDelegate);
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors(offsetStore));
    return lazyDescriptors;
  }

  private static void assertIntentionActionDescriptorsAreRangeMarkerBased(@NotNull List<? extends IntentionActionDescriptor> descriptors) {
    for (IntentionActionDescriptor descriptor : descriptors) {
      assert descriptor.myFixRange  == null || descriptor.myFixRange instanceof RangeMarker : descriptor +"; descriptors:"+descriptors;
    }
  }

  /**
   * Starts computing lazy quick fixes in the background.
   * The result will be stored back in {@link OffsetStore#lazyQuickFixes} inside {@link LazyFixDescription#future}
   */
  void startComputeQuickFixes(@NotNull Document document, @NotNull Project project) {
    assertIntentionActionDescriptorsAreRangeMarkerBased(getIntentionActionDescriptors(offsetStore));
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    AtomicReference<ProgressIndicator> progressIndicator = new AtomicReference<>(new DaemonProgressIndicator());
    update(oldStore -> {
      progressIndicator.get().cancel(); // cancel the previous computations started before but not stored in the "future" field because the CAS failed
      progressIndicator.set(new DaemonProgressIndicator());
      List<LazyFixDescription> newLazyFixes = ContainerUtil.map(oldStore.lazyQuickFixes(), description -> {
        Future<List<IntentionActionDescriptor>> future = description.future();
        if (future == null) {
          Consumer<? super QuickFixActionRegistrar> computer = description.fixesComputer();
          future = ReadAction.nonBlocking(() -> {
            AtomicReference<List<IntentionActionDescriptor>> result = new AtomicReference<>(List.of());
            ((ApplicationEx)ApplicationManager.getApplication()).executeByImpatientReader(
              () -> result.set(doComputeLazyQuickFixes(document, project, description.psiModificationStamp(), computer)));
            assert result.get() != null;
            return result.get();
          }).wrapProgress(progressIndicator.get()).submit(ForkJoinPool.commonPool());
          return new LazyFixDescription(computer, PsiManager.getInstance(project).getModificationTracker().getModificationCount(),
                                        future, progressIndicator.get());
        }
        return description;
      });
      return oldStore.withLazyQuickFixes(newLazyFixes);
    });
  }

  void copyComputedLazyFixesTo(@NotNull HighlightInfo newInfo, @NotNull Document document) {
    newInfo.update(store -> {
      List<? extends LazyFixDescription> oldFixes = this.offsetStore.lazyQuickFixes();
      List<? extends LazyFixDescription> newFixes = store.lazyQuickFixes();
      if (newFixes.size() == oldFixes.size() && psiModificationStampIsTheSame(newFixes, oldFixes)) {
        OffsetStore newO = store.withLazyQuickFixes(oldFixes);
        return updateFields(newO, document);
      }
      return store;
    });
  }

  private static boolean psiModificationStampIsTheSame(@NotNull @Unmodifiable List<? extends LazyFixDescription> list1,
                                                       @NotNull @Unmodifiable List<? extends LazyFixDescription> list2) {
    for (int i = 0; i < list1.size(); i++) {
      LazyFixDescription fix1 = list1.get(i);
      LazyFixDescription fix2 = list2.get(i);
      if (fix1.psiModificationStamp() != fix2.psiModificationStamp()) {
        return false;
      }
    }
    return true;
  }

  @ApiStatus.Internal
  @NotNull
  public Builder copy(boolean copyFlagsAndFixes) {
    HighlightInfoB builder = new HighlightInfoB(type, true) {
      @Override
      public @NotNull HighlightInfo createUnconditionally() {
        HighlightInfo newInfo = super.createUnconditionally();
        newInfo.update(oldStore -> {
          OffsetStore myStore = offsetStore;
          return (copyFlagsAndFixes ?
                  oldStore.withIntentionDescriptorsAndFixMarker(myStore.intentionActionDescriptors(), myStore.fixMarker()) :
                  oldStore)
            .withLazyQuickFixes(myStore.lazyQuickFixes());
        });
        if (copyFlagsAndFixes) {
          newInfo.myFlags = myFlags;
        }
        newInfo.toolId = toolId;
        return newInfo;
      }
    };
    builder.range(startOffset, endOffset);
    if (forcedTextAttributes != null) {
      builder.textAttributes(forcedTextAttributes);
    }
    if (forcedTextAttributesKey != null) {
      builder.textAttributes(forcedTextAttributesKey);
    }
    if (description != null) {
      builder.description(description);
    }
    if (toolTip != null) {
      builder.escapedToolTip(toolTip);
    }
    if (isFileLevelAnnotation()) {
      builder.fileLevelAnnotation();
    }
    if (copyFlagsAndFixes && isAfterEndOfLine()) {
      builder.endOfLine();
    }
    builder.severity(severity);
    if (navigationShift != 0) {
      builder.navigationShift(navigationShift);
    }
    if (getProblemGroup() != null) {
      builder.problemGroup(getProblemGroup());
    }
    if (gutterIconRenderer instanceof GutterIconRenderer g) {
      builder.gutterIconRenderer(g);
    }
    if (group != 0) {
      builder.group(group);
    }
    return builder;
  }
}
