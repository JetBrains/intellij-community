// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.impl.SweepProcessor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Document markup manipulation methods during the highlighting, in the background thread under read action.
 * Must be used inside the highlighting process only (e.g., in your {@link HighlightingPass#collectInformation(ProgressIndicator)})
 */
@ApiStatus.Experimental
public final class BackgroundUpdateHighlightersUtil {
  private static final Logger LOG = Logger.getInstance(BackgroundUpdateHighlightersUtil.class);
  // return true if added
  static boolean addHighlighterToEditorIncrementally(@NotNull HighlightingSession session,
                                                     @NotNull PsiFile file,
                                                     @NotNull Document document,
                                                     @NotNull TextRange restrictRange,
                                                     @NotNull HighlightInfo info,
                                                     @Nullable EditorColorsScheme colorsScheme, // if null, the global scheme will be used
                                                     int group,
                                                     @NotNull Long2ObjectMap<RangeMarker> range2markerCache) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = file.getProject();
    if (!UpdateHighlightersUtil.HighlightInfoPostFilters.accept(project, info)) {
      return false;
    }

    if (UpdateHighlightersUtil.isFileLevelOrGutterAnnotation(info)) return false;
    if (!restrictRange.intersects(info)) return false;

    MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    boolean myInfoIsError = UpdateHighlightersUtil.isSevere(info, severityRegistrar);
    HighlightersRecycler recycler = new HighlightersRecycler();
    Processor<HighlightInfo> otherHighlightInTheWayProcessor = oldInfo -> {
      if (!myInfoIsError && UpdateHighlightersUtil.isCovered(info, severityRegistrar, oldInfo)) {
        return false;
      }
      RangeHighlighterEx oldHighlighter = oldInfo.highlighter;
      if (oldHighlighter != null && oldInfo.equals(info)) {
        recycler.recycleHighlighter(oldHighlighter);
      }
      return !(Objects.equals(oldInfo.toolId, info.toolId) && oldInfo.equalsByActualOffset(info));
    };
    boolean allIsClear = DaemonCodeAnalyzerEx.processHighlights(document, project,
                                                                null, info.getActualStartOffset(), info.getActualEndOffset(),
                                                                otherHighlightInTheWayProcessor);
    try {
      if (allIsClear) {
        createOrReuseHighlighterFor(info, colorsScheme, document, group, file, (MarkupModelEx)markup, recycler, range2markerCache, severityRegistrar);
        UpdateHighlightersUtil.incinerateObsoleteHighlighters(recycler, session);
        UpdateHighlightersUtil.clearWhiteSpaceOptimizationFlag(document);
        return true;
      }
    }
    finally {
      recycler.releaseHighlighters();
    }
    return false;
  }

  public static void setHighlightersToEditor(@NotNull Project project,
                                             @NotNull PsiFile psiFile,
                                             @NotNull Document document,
                                             int startOffset,
                                             int endOffset,
                                             @NotNull Collection<? extends HighlightInfo> highlights,
                                             int group) {
    HighlightingSession session = HighlightingSessionImpl.getFromCurrentIndicator(psiFile);
    MarkupModelEx markup = (MarkupModelEx)DocumentMarkupModel.forDocument(document, project, true);
    TextRange range = new TextRange(startOffset, endOffset);
    setHighlightersInRange(range, new ArrayList<>(highlights), markup, group, session);
  }

  // set highlights inside startOffset,endOffset but outside priorityRange
  static void setHighlightersOutsideRange(@NotNull List<? extends HighlightInfo> infos,
                                          @NotNull TextRange restrictedRange,
                                          @NotNull TextRange priorityRange,
                                          int group,
                                          @NotNull HighlightingSession session) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile psiFile = session.getPsiFile();
    Project project = session.getProject();
    List<HighlightInfo> filteredInfos = UpdateHighlightersUtil.HighlightInfoPostFilters.applyPostFilter(project, infos);
    Document document = session.getDocument();
    MarkupModel markup = DocumentMarkupModel.forDocument(document, project, true);

    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    HighlightersRecycler toReuse = new HighlightersRecycler();
    ContainerUtil.quickSort(filteredInfos, UpdateHighlightersUtil.BY_ACTUAL_START_OFFSET_NO_DUPS);
    Set<HighlightInfo> infoSet = new HashSet<>(filteredInfos);

    Processor<HighlightInfo> processor = info -> {
      if (info.getGroup() == group) {
        RangeHighlighterEx highlighter = info.getHighlighter();
        int hiStart = highlighter.getStartOffset();
        int hiEnd = highlighter.getEndOffset();

        if (!info.isFromInjection() && hiEnd < document.getTextLength() && !restrictedRange.contains(highlighter) && hiEnd != 0) {
          return true; // injections are oblivious to restricting range
        }
        boolean toRemove = infoSet.contains(info) ||
                           !priorityRange.containsRange(hiStart, hiEnd) &&
                           (hiEnd != document.getTextLength() || priorityRange.getEndOffset() != document.getTextLength());
        if (toRemove) {
          toReuse.recycleHighlighter(highlighter);
        }
      }
      return true;
    };
    Long2ObjectMap<RangeMarker> range2markerCache = new Long2ObjectOpenHashMap<>(10);
    boolean[] changed = {false};
    SweepProcessor.Generator<HighlightInfo> generator = proc -> ContainerUtil.process(filteredInfos, proc);
    List<HighlightInfo> fileLevelHighlights = new ArrayList<>();
    List<HighlightInfo> infosToCreateHighlightersFor = new ArrayList<>(filteredInfos.size());

    try {
      DaemonCodeAnalyzerEx.processHighlightsOverlappingOutside(document, project, priorityRange.getStartOffset(), priorityRange.getEndOffset(), processor);
      SweepProcessor.sweep(generator, (offset, info, atStart, overlappingIntervals) -> {
        if (!atStart) return true;
        if (!info.isFromInjection() && info.getEndOffset() < document.getTextLength() && !restrictedRange.contains(info)) {
          return true; // injections are oblivious to restricting range
        }

        if (info.isFileLevelAnnotation()) {
          fileLevelHighlights.add(info);
          changed[0] = true;
          return true;
        }
        if (UpdateHighlightersUtil.isWarningCoveredByError(info, severityRegistrar, overlappingIntervals)) {
          return true;
        }
        if (info.getStartOffset() < priorityRange.getStartOffset() || info.getEndOffset() > priorityRange.getEndOffset()) {
          // have to create RangeHighlighter later, to avoid exposing them to the markup model immediately,
          // thus messing the HighlightInfo.getStartOffset() leading to "sweep generator supplied infos in a wrong order" exception
          infosToCreateHighlightersFor.add(info);
          changed[0] = true;
        }
        return true;
      });
      for (HighlightInfo info : infosToCreateHighlightersFor) {
        createOrReuseHighlighterFor(info, session.getColorsScheme(), document, group, psiFile, (MarkupModelEx)markup, toReuse, range2markerCache, severityRegistrar);
      }
      boolean shouldClean = restrictedRange.getStartOffset() == 0 && restrictedRange.getEndOffset() == document.getTextLength();
      session.updateFileLevelHighlights(fileLevelHighlights, group, shouldClean);
      changed[0] |= UpdateHighlightersUtil.incinerateObsoleteHighlighters(toReuse, session);
    }
    finally {
      toReuse.releaseHighlighters();
    }
    if (changed[0]) {
      UpdateHighlightersUtil.clearWhiteSpaceOptimizationFlag(document);
    }
  }

  static void setHighlightersInRange(@NotNull TextRange range,
                                     @NotNull List<? extends HighlightInfo> infos,
                                     @NotNull MarkupModelEx markup,
                                     int group,
                                     @NotNull HighlightingSession session) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Project project = session.getProject();
    List<HighlightInfo> toReuse = new ArrayList<>();
    Document document = session.getDocument();

    DaemonCodeAnalyzerEx.processHighlights(markup, project, null, range.getStartOffset(), range.getEndOffset(), info -> {
      if (info.getGroup() == group) {
        int hiEnd = info.getEndOffset();
        boolean willBeRemoved = range.contains(info)
                                || hiEnd == document.getTextLength() && range.getEndOffset() == hiEnd;
        if (willBeRemoved) {
          toReuse.add(info);
        }
      }
      return true;
    });

    setHighlightersInRange(range, infos, markup, group, session, toReuse);
  }
  static void setHighlightersInRange(@NotNull TextRange range,
                                     @NotNull List<? extends HighlightInfo> infos,
                                     @NotNull MarkupModelEx markup,
                                     int group,
                                     @NotNull HighlightingSession session,
                                     @NotNull List<? extends HighlightInfo> toRemove) {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile psiFile = session.getPsiFile();
    Project project = session.getProject();
    SeverityRegistrar severityRegistrar = SeverityRegistrar.getSeverityRegistrar(project);
    HighlightersRecycler toReuse = new HighlightersRecycler();
    Document document = session.getDocument();
    Long2ObjectMap<RangeMarker> range2markerCache = new Long2ObjectOpenHashMap<>(10);
    boolean[] changed = {false};

    try {
      for (HighlightInfo info : toRemove) {
        RangeHighlighterEx highlighter = info.getHighlighter();
        if (highlighter != null) {
          toReuse.recycleHighlighter(highlighter);
        }
      }
      List<HighlightInfo> filteredInfos = UpdateHighlightersUtil.HighlightInfoPostFilters.applyPostFilter(project, infos);
      ContainerUtil.quickSort(filteredInfos, UpdateHighlightersUtil.BY_ACTUAL_START_OFFSET_NO_DUPS);
      SweepProcessor.Generator<HighlightInfo> generator = processor -> ContainerUtil.process(filteredInfos, processor);
      List<HighlightInfo> fileLevelHighlights = new ArrayList<>();
      List<HighlightInfo> infosToCreateHighlightersFor = new ArrayList<>(filteredInfos.size());
      SweepProcessor.sweep(generator, (__, info, atStart, overlappingIntervals) -> {
        if (!atStart) {
          return true;
        }
        if (info.isFileLevelAnnotation()) {
          fileLevelHighlights.add(info);
          changed[0] = true;
          return true;
        }

        if (range.contains(info) && !UpdateHighlightersUtil.isWarningCoveredByError(info, severityRegistrar, overlappingIntervals)) {
          // have to create RangeHighlighter later, to avoid exposing them to the markup model immediately,
          // thus messing the HighlightInfo.getStartOffset() leading to "sweep generator supplied infos in a wrong order" exception
          infosToCreateHighlightersFor.add(info);
          changed[0] = true;
        }
        return true;
      });
      for (HighlightInfo info : infosToCreateHighlightersFor) {
        createOrReuseHighlighterFor(info, session.getColorsScheme(), document, group, psiFile, markup, toReuse, range2markerCache, severityRegistrar);
      }
      session.updateFileLevelHighlights(fileLevelHighlights, group, range.equalsToRange(0, document.getTextLength()));
      changed[0] |= UpdateHighlightersUtil.incinerateObsoleteHighlighters(toReuse, session);
    }
    finally {
      toReuse.releaseHighlighters();
    }

    if (changed[0]) {
      UpdateHighlightersUtil.clearWhiteSpaceOptimizationFlag(document);
    }
  }

  static void createOrReuseHighlighterFor(@NotNull HighlightInfo info,
                                          @Nullable EditorColorsScheme colorsScheme, // if null, the global scheme will be used
                                          @NotNull Document document,
                                          int group,
                                          @NotNull PsiFile psiFile,
                                          @NotNull MarkupModelEx markup,
                                          @Nullable HighlighterRecyclerPickup infosToRemove,
                                          @NotNull Long2ObjectMap<RangeMarker> range2markerCache,
                                          @NotNull SeverityRegistrar severityRegistrar) {
    assert !info.isFileLevelAnnotation();
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

    int layer = UpdateHighlightersUtil.getLayer(info, severityRegistrar);
    long finalInfoRange = TextRangeScalarUtil.toScalarRange(infoStartOffset, infoEndOffset);
    TextAttributes infoAttributes = info.getTextAttributes(psiFile, colorsScheme);
    Consumer<RangeHighlighterEx> changeAttributes = finalHighlighter -> {
      TextAttributesKey textAttributesKey = info.forcedTextAttributesKey == null ? info.type.getAttributesKey() : info.forcedTextAttributesKey;
      finalHighlighter.setTextAttributesKey(textAttributesKey);

      if (infoAttributes == TextAttributes.ERASE_MARKER ||
          infoAttributes != null && !infoAttributes.equals(finalHighlighter.getTextAttributes(colorsScheme))) {
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

    RangeHighlighterEx salvagedFromBin = infosToRemove == null ? null : infosToRemove.pickupHighlighterFromGarbageBin(infoStartOffset, infoEndOffset, layer);
    RangeHighlighterEx highlighter;
    if (salvagedFromBin == null) {
      highlighter = markup.addRangeHighlighterAndChangeAttributes(null, infoStartOffset, infoEndOffset, layer,
                                                                  HighlighterTargetArea.EXACT_RANGE, false, changeAttributes);
      if (HighlightInfoType.VISIBLE_IF_FOLDED.contains(info.type)) {
        highlighter.setVisibleIfFolded(true);
      }
    }
    else {
      highlighter = salvagedFromBin;
      markup.changeAttributesInBatch(highlighter, changeAttributes);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("createOrReuseHighlighterFor "+info +(salvagedFromBin == null ? "" : " (recycled)"));
    }
    if (infoAttributes != null) {
      TextAttributes actualAttributes = highlighter.getTextAttributes(colorsScheme);
      boolean attributesSet = Comparing.equal(infoAttributes, actualAttributes);
      if (!attributesSet) {
        highlighter.setTextAttributes(infoAttributes);
        TextAttributes afterSet = highlighter.getTextAttributes(colorsScheme);
        LOG.error("Expected to set " + infoAttributes + " but actual attributes are: " + actualAttributes +
                  "; colorsScheme: '" + (colorsScheme == null ? "[global]" : colorsScheme.getName()) + "'" +
                  "; highlighter:" + highlighter + " (" + highlighter.getClass() + ")" +
                  "; was reused from the bin: " + (salvagedFromBin != null) +
                  "; is being recycled: " + HighlightersRecycler.isBeingRecycled(highlighter)+
                  "; markup: " + markup + " (" + markup.getClass() + ")" +
                  "; attributes after the second .setAttributes(): " + afterSet +
                  " (set " + (infoAttributes.equals(afterSet) ? "successfully" : "not successfully") + ")");
      }
    }
  }
}
