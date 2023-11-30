// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.SweepProcessor;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.psi.PsiCompiledFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Document markup manipulation methods during the highlighting.
 * Must be used inside the highlighting process only (e.g., in your {@link HighlightingPass#applyInformationToEditor()})
 */
public final class UpdateHighlightersUtil {
  static final Comparator<HighlightInfo> BY_ACTUAL_START_OFFSET_NO_DUPS = (o1, o2) -> {
    int d = o1.getActualStartOffset() - o2.getActualStartOffset();
    if (d != 0) return d;
    d = o1.getActualEndOffset() - o2.getActualEndOffset();
    if (d != 0) return d;

    d = Comparing.compare(o1.getSeverity(), o2.getSeverity());
    if (d != 0) return -d; // higher severity first, to prevent warnings overlap errors

    if (!Comparing.equal(o1.type, o2.type)) {
      return String.valueOf(o1.type).compareTo(String.valueOf(o2.type));
    }

    if (!Comparing.equal(o1.getGutterIconRenderer(), o2.getGutterIconRenderer())) {
      return String.valueOf(o1.getGutterIconRenderer()).compareTo(String.valueOf(o2.getGutterIconRenderer()));
    }

    if (!Comparing.equal(o1.forcedTextAttributes, o2.forcedTextAttributes)) {
      return String.valueOf(o1.getGutterIconRenderer()).compareTo(String.valueOf(o2.getGutterIconRenderer()));
    }

    if (!Comparing.equal(o1.forcedTextAttributesKey, o2.forcedTextAttributesKey)) {
      return String.valueOf(o1.getGutterIconRenderer()).compareTo(String.valueOf(o2.getGutterIconRenderer()));
    }

    return Comparing.compare(o1.getDescription(), o2.getDescription());
  };

  private static boolean isCoveredByOffsets(@NotNull HighlightInfo info, @NotNull HighlightInfo coveredBy) {
    return coveredBy.startOffset <= info.startOffset && info.endOffset <= coveredBy.endOffset
           && info.getGutterIconRenderer() == null;
  }

  static final class HighlightInfoPostFilters {
    private static final ExtensionPointName<HighlightInfoPostFilter> EP_NAME = new ExtensionPointName<>("com.intellij.highlightInfoPostFilter");
    static boolean accept(@NotNull Project project, @NotNull HighlightInfo info) {
      for (HighlightInfoPostFilter filter : EP_NAME.getExtensionList(project)) {
        if (!filter.accept(info))
          return false;
      }

      return true;
    }
    static @NotNull List<HighlightInfo> applyPostFilter(@NotNull Project project, @NotNull List<? extends HighlightInfo> highlightInfos) {
      List<HighlightInfo> result = new ArrayList<>(highlightInfos.size());
      for (HighlightInfo info : highlightInfos) {
        if (accept(project, info)) {
          result.add(info);
        }
      }
      return result;
    }
  }

  public static boolean isFileLevelOrGutterAnnotation(@NotNull HighlightInfo info) {
    return info.isFileLevelAnnotation() || info.getGutterIconRenderer() != null;
  }


  public static void setHighlightersToSingleEditor(@NotNull Project project,
                                                   @NotNull Editor editor,
                                                   int startOffset,
                                                   int endOffset,
                                                   @NotNull Collection<? extends HighlightInfo> highlights,
                                                   @Nullable EditorColorsScheme colorsScheme, // if null, the global scheme will be used
                                                   int group) {
    ThreadingAssertions.assertEventDispatchThread();
    Document document = editor.getDocument();
    MarkupModelEx markup = (MarkupModelEx)editor.getMarkupModel();
    setHighlightersToEditor(project, document, startOffset, endOffset, highlights, colorsScheme, group, markup);
  }

  public static void setHighlightersToEditor(@NotNull Project project,
                                             @NotNull Document document,
                                             int startOffset,
                                             int endOffset,
                                             @NotNull Collection<? extends HighlightInfo> highlights,
                                             @Nullable EditorColorsScheme colorsScheme, // if null, the global scheme will be used
                                             int group) {
    ThreadingAssertions.assertEventDispatchThread();
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    setHighlightersToEditor(project, document, startOffset, endOffset, highlights, colorsScheme, group, markup);
  }

  private static void setHighlightersToEditor(@NotNull Project project,
                                              @NotNull Document document,
                                              int startOffset,
                                              int endOffset,
                                              @NotNull Collection<? extends HighlightInfo> infos,
                                              @Nullable EditorColorsScheme colorsScheme, // if null, the global scheme will be used
                                              int group,
                                              @NotNull MarkupModelEx markup) {
    TextRange range = new TextRange(startOffset, endOffset);
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile instanceof PsiCompiledFile) {
      psiFile = ((PsiCompiledFile)psiFile).getDecompiledPsiFile();
    }
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    if (psiFile != null) {
      codeAnalyzer.cleanFileLevelHighlights(group, psiFile);
    }

    if (psiFile != null) {
      HighlightingSession session = HighlightingSessionImpl.createHighlightingSession(psiFile, new DaemonProgressIndicator(), colorsScheme, ProperTextRange.create(startOffset, endOffset), CanISilentlyChange.Result.UH_UH,
                                                                                      0);
      setHighlightersInRange(document, range, new ArrayList<>(infos), markup, group, session);
    }
  }

  private static void setHighlightersInRange(@NotNull Document document,
                                             @NotNull TextRange range,
                                             @NotNull List<? extends HighlightInfo> infos,
                                             @NotNull MarkupModelEx markup,
                                             int group,
                                             @NotNull HighlightingSession session) {
    ThreadingAssertions.assertEventDispatchThread();
    Project project = session.getProject();
    PsiFile psiFile = session.getPsiFile();
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    HighlightersRecycler infosToRemove = new HighlightersRecycler();
    DaemonCodeAnalyzerEx.processHighlights(markup, project, null, range.getStartOffset(), range.getEndOffset(), info -> {
      if (info.getGroup() == group) {
        RangeHighlighterEx highlighter = info.getHighlighter();
        int hiStart = highlighter.getStartOffset();
        int hiEnd = highlighter.getEndOffset();
        boolean willBeRemoved = range.containsRange(hiStart, hiEnd)
                                || hiEnd == document.getTextLength() && range.getEndOffset() == hiEnd;
        if (willBeRemoved && infosToRemove.recycleHighlighter(highlighter)) {
          info.setHighlighter(null);
        }
      }
      return true;
    });

    List<HighlightInfo> filteredInfos = HighlightInfoPostFilters.applyPostFilter(project, infos);
    ContainerUtil.quickSort(filteredInfos, BY_ACTUAL_START_OFFSET_NO_DUPS);
    Long2ObjectMap<RangeMarker> range2markerCache = new Long2ObjectOpenHashMap<>(10);
    DaemonCodeAnalyzerEx codeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(project);
    boolean[] changed = {false};
    SweepProcessor.Generator<HighlightInfo> generator = processor -> ContainerUtil.process(filteredInfos, processor);
    SweepProcessor.sweep(generator, (__, info, atStart, overlappingIntervals) -> {
      if (!atStart) {
        return true;
      }
      if (info.isFileLevelAnnotation()) {
        codeAnalyzer.addFileLevelHighlight(group, info, psiFile);
        changed[0] = true;
        return true;
      }

      if (range.contains(info) && !isWarningCoveredByError(info, severityRegistrar, overlappingIntervals)) {
        createOrReuseHighlighterFor(info, session.getColorsScheme(), document, group, psiFile, markup, infosToRemove, range2markerCache, severityRegistrar);
        changed[0] = true;
      }
      return true;
    });

    changed[0] |= incinerateObsoleteHighlighters(infosToRemove, session);

    if (changed[0]) {
      clearWhiteSpaceOptimizationFlag(document);
    }
  }

  static boolean incinerateObsoleteHighlighters(@NotNull HighlightersRecycler infosToRemove, @NotNull HighlightingSession session) {
    boolean changed = false;
    // do not remove obsolete highlighters if we are in "essential highlighting only" mode, because otherwise all inspection-produced results would be gone
    for (RangeHighlighter highlighter : infosToRemove.forAllInGarbageBin()) {
      if (shouldRemoveHighlighter(highlighter, session)) {
        highlighter.dispose();
        changed = true;
      }
    }
    return changed;
  }

  static boolean shouldRemoveHighlighter(@NotNull RangeHighlighter highlighter, @NotNull HighlightingSession session) {
    return !session.isEssentialHighlightingOnly()
           || shouldRemoveInfoEvenInEssentialMode(highlighter);
  }

  private static boolean shouldRemoveInfoEvenInEssentialMode(@NotNull RangeHighlighter highlighter) {
    HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
    if (info == null) return true;
    int group = info.getGroup();
    if (group != Pass.LOCAL_INSPECTIONS
        && group != Pass.EXTERNAL_TOOLS
        && group != Pass.WHOLE_FILE_LOCAL_INSPECTIONS
        && group != Pass.UPDATE_ALL
        && group != GeneralHighlightingPass.POST_UPDATE_ALL
    ) {
      return true;
    }

    // update highlight if it's a symbol type (field/statics/etc), otherwise don't touch it (could have been e.g., unused symbol highlight)
    return group == Pass.UPDATE_ALL && (
      info.getSeverity() == HighlightInfoType.SYMBOL_TYPE_SEVERITY || info.getSeverity() == HighlightSeverity.ERROR);
  }

  static boolean isWarningCoveredByError(@NotNull HighlightInfo info,
                                         @NotNull SeverityRegistrar severityRegistrar,
                                         @NotNull Collection<? extends HighlightInfo> overlappingIntervals) {
    if (!isSevere(info, severityRegistrar)) {
      for (HighlightInfo overlapping : overlappingIntervals) {
        if (isCovered(info, severityRegistrar, overlapping)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean isCovered(@NotNull HighlightInfo warning, @NotNull SeverityRegistrar severityRegistrar, @NotNull HighlightInfo candidate) {
    if (!isCoveredByOffsets(warning, candidate)) return false;
    if (candidate.getSeverity() == HighlightInfoType.SYMBOL_TYPE_SEVERITY) return false; // syntax should not interfere with warnings
    return isSevere(candidate, severityRegistrar);
  }

  static boolean isSevere(@NotNull HighlightInfo info, @NotNull SeverityRegistrar severityRegistrar) {
    HighlightSeverity severity = info.getSeverity();
    return severityRegistrar.compare(HighlightSeverity.ERROR, severity) <= 0 || severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY;
  }

  private static void createOrReuseHighlighterFor(@NotNull HighlightInfo info,
                                                  @Nullable EditorColorsScheme colorsScheme, // if null, the global scheme will be used
                                                  @NotNull Document document,
                                                  int group,
                                                  @NotNull PsiFile psiFile,
                                                  @NotNull MarkupModelEx markup,
                                                  @Nullable HighlightersRecycler infosToRemove,
                                                  @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                                  @NotNull SeverityRegistrar severityRegistrar) {
    int infoStartOffset = info.startOffset;
    int infoEndOffset = info.endOffset;

    int docLength = document.getTextLength();
    if (infoEndOffset > docLength) {
      infoEndOffset = docLength;
      infoStartOffset = Math.min(infoStartOffset, infoEndOffset);
    }
    if (infoEndOffset == infoStartOffset && !info.isAfterEndOfLine()) {
      if (infoEndOffset == docLength) return;  // empty highlighter beyond file boundaries
      infoEndOffset++; //show something in case of empty HighlightInfo
    }

    info.setGroup(group);

    int layer = getLayer(info, severityRegistrar);
    long finalInfoRange = TextRangeScalarUtil.toScalarRange(infoStartOffset, infoEndOffset);
    TextAttributes infoAttributes = info.getTextAttributes(psiFile, colorsScheme);
    Consumer<RangeHighlighterEx> changeAttributes = finalHighlighter -> {
      TextAttributesKey textAttributesKey = info.forcedTextAttributesKey == null ? info.type.getAttributesKey() : info.forcedTextAttributesKey;
      finalHighlighter.setTextAttributesKey(textAttributesKey);

      if (infoAttributes != null && !infoAttributes.equals(finalHighlighter.getTextAttributes(colorsScheme)) ||
              infoAttributes == TextAttributes.ERASE_MARKER) {
        finalHighlighter.setTextAttributes(infoAttributes);
      }

      info.setHighlighter(finalHighlighter);
      finalHighlighter.setAfterEndOfLine(info.isAfterEndOfLine());

      Color infoErrorStripeColor = info.getErrorStripeMarkColor(psiFile, colorsScheme);
      TextAttributes attributes = finalHighlighter.getTextAttributes(colorsScheme);
      Color attributesErrorStripeColor = attributes != null ? attributes.getErrorStripeColor() : null;
      if (infoErrorStripeColor != null && !infoErrorStripeColor.equals(attributesErrorStripeColor)) {
        finalHighlighter.setErrorStripeMarkColor(infoErrorStripeColor);
      }

      if (info != finalHighlighter.getErrorStripeTooltip()) {
        finalHighlighter.setErrorStripeTooltip(info);
      }
      GutterMark renderer = info.getGutterIconRenderer();
      finalHighlighter.setGutterIconRenderer((GutterIconRenderer)renderer);

      range2markerCache.put(finalInfoRange, finalHighlighter);
      info.updateQuickFixFields(document, range2markerCache, finalInfoRange);
    };

    RangeHighlighterEx highlighter = infosToRemove == null ? null : infosToRemove.pickupHighlighterFromGarbageBin(infoStartOffset, infoEndOffset, layer);
    if (highlighter == null) {
      highlighter = markup.addRangeHighlighterAndChangeAttributes(null, infoStartOffset, infoEndOffset, layer,
                                                                  HighlighterTargetArea.EXACT_RANGE, false, changeAttributes);
      if (HighlightInfoType.VISIBLE_IF_FOLDED.contains(info.type)) {
        highlighter.setVisibleIfFolded(true);
      }
    }
    else {
      markup.changeAttributesInBatch(highlighter, changeAttributes);
    }

    if (infoAttributes != null) {
      boolean attributesSet = Comparing.equal(infoAttributes, highlighter.getTextAttributes(colorsScheme));
      assert attributesSet : "Info: " + infoAttributes +
                             "; colorsScheme: " + (colorsScheme == null ? "[global]" : colorsScheme.getName()) +
                             "; highlighter:" + highlighter.getTextAttributes(colorsScheme);
    }
  }

  private static class InternalLayerSuppliers {
    private static final ExtensionPointName<InternalLayerSupplier> EP_NAME = ExtensionPointName.create("com.intellij.internalHighlightingLayerSupplier");
    private static int getLayerFromSuppliers(@NotNull HighlightInfo info) {
      for (InternalLayerSupplier extension : EP_NAME.getExtensions()) {
        int layer = extension.getLayer(info);
        if (layer > 0) {
          return layer;
        }
      }
      return -1;
    }
  }

  static int getLayer(@NotNull HighlightInfo info, @NotNull SeverityRegistrar severityRegistrar) {
    int hardCodedLayer = InternalLayerSuppliers.getLayerFromSuppliers(info);
    if (hardCodedLayer > 0) {
      return hardCodedLayer;
    }
    HighlightSeverity severity = info.getSeverity();
    int layer;
    if (severityRegistrar.compare(severity, HighlightSeverity.ERROR) >= 0) {
      layer = HighlighterLayer.ERROR;
    }
    else if (severityRegistrar.compare(severity, HighlightSeverity.WARNING) >= 0) {
      layer = HighlighterLayer.WARNING;
    }
    else if (severityRegistrar.compare(severity, HighlightSeverity.WEAK_WARNING) >= 0 || severity == HighlightSeverity.TEXT_ATTRIBUTES) {
      layer = HighlighterLayer.WEAK_WARNING;
    }
    else if (severity == HighlightInfoType.INJECTED_FRAGMENT_SEVERITY || severity == HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY) {
      layer = HighlighterLayer.CARET_ROW - 1;
    }
    else if (severity == HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY) {
      layer = HighlighterLayer.CARET_ROW - 2;
    }
    else if (severity == HighlightInfoType.ELEMENT_UNDER_CARET_SEVERITY) {
      layer = HighlighterLayer.ELEMENT_UNDER_CARET;
    }
    else if (severityRegistrar.getAllSeverities().contains(severity) && !SeverityRegistrar.isDefaultSeverity(severity)) {
      layer = HighlighterLayer.WARNING;
    }
    else {
      layer = HighlighterLayer.ADDITIONAL_SYNTAX;
    }
    return layer;
  }

  private static final Key<Boolean> TYPING_INSIDE_HIGHLIGHTER_OCCURRED = Key.create("TYPING_INSIDE_HIGHLIGHTER_OCCURRED");
  static boolean isWhitespaceOptimizationAllowed(@NotNull Document document) {
    return document.getUserData(TYPING_INSIDE_HIGHLIGHTER_OCCURRED) == null;
  }
  private static void disableWhiteSpaceOptimization(@NotNull Document document) {
    document.putUserData(TYPING_INSIDE_HIGHLIGHTER_OCCURRED, Boolean.TRUE);
  }
  static void clearWhiteSpaceOptimizationFlag(@NotNull Document document) {
    document.putUserData(TYPING_INSIDE_HIGHLIGHTER_OCCURRED, null);
  }

  static void updateHighlightersByTyping(@NotNull Project project, @NotNull DocumentEvent e) {
    ThreadingAssertions.assertEventDispatchThread();

    Document document = e.getDocument();
    if (document.isInBulkUpdate()) return;

    int start = e.getOffset() - 1;
    int end = start + e.getOldLength();

    List<HighlightInfo> toRemove = new ArrayList<>();
    DaemonCodeAnalyzerEx.processHighlights(document, project, null, start, end, info -> {
      if (!info.needUpdateOnTyping()) return true;

      RangeHighlighter highlighter = info.getHighlighter();
      int highlighterStart = highlighter.getStartOffset();
      int highlighterEnd = highlighter.getEndOffset();
      if (info.isAfterEndOfLine()) {
        if (highlighterStart < document.getTextLength()) {
          highlighterStart += 1;
        }
        if (highlighterEnd < document.getTextLength()) {
          highlighterEnd += 1;
        }
      }
      if (!highlighter.isValid() || start < highlighterEnd && highlighterStart <= end) {
        toRemove.add(info);
      }
      return true;
    });

    for (HighlightInfo info : toRemove) {
      if (!info.getHighlighter().isValid() || info.type.equals(HighlightInfoType.WRONG_REF)) {
        info.getHighlighter().dispose();
      }
    }

    if (!toRemove.isEmpty()) {
      disableWhiteSpaceOptimization(document);
    }
  }

  /**
   * Remove all highlighters with exactly the given range from {@link DocumentMarkupModel}.
   * This might be useful in quick fixes and intention actions to provide immediate feedback.
   * Note that all highlighters at the given range are removed, not only the ones produced by your inspection,
   * but most likely that will look fine:
   * they'll be restored when the new highlighting pass is finished.
   * This method currently works in O(total highlighter count in file) time.
   */
  public static void removeHighlightersWithExactRange(@NotNull Document document, @NotNull Project project, @NotNull Segment range) {
    if (IntentionPreviewUtils.isIntentionPreviewActive()) return;
    ThreadingAssertions.assertEventDispatchThread();
    MarkupModel model = DocumentMarkupModel.forDocument(document, project, false);
    if (model == null) return;

    for (RangeHighlighter highlighter : model.getAllHighlighters()) {
      if (TextRange.areSegmentsEqual(range, highlighter)) {
        model.removeHighlighter(highlighter);
      }
    }
  }

}
