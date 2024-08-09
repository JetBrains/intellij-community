// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.diagnostic.PluginException;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.TextRangeScalarUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.InjectionUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.NotNullList;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public final class LineMarkersPass extends TextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance(LineMarkersPass.class);

  private final @NotNull PsiFile myFile;
  private final @NotNull TextRange myPriorityBounds;
  private final @NotNull TextRange myRestrictRange;

  private final @NotNull Mode myMode;
  private final HighlightingSession myHighlightingSession;

  LineMarkersPass(@NotNull Project project,
                  @NotNull PsiFile file,
                  @NotNull Document document,
                  @NotNull TextRange priorityBounds,
                  @NotNull TextRange restrictRange,
                  @NotNull LineMarkersPass.Mode mode,
                  @NotNull HighlightingSession session) {
    super(project, document, false);
    myFile = file;
    myPriorityBounds = priorityBounds;
    myRestrictRange = restrictRange;
    myMode = mode;
    myHighlightingSession = session;
  }

  @Override
  public void doApplyInformationToEditor() {
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    List<LineMarkerInfo<?>> markers = doCollectMarkers();
    try {
      LineMarkersUtil.setLineMarkersToEditor(myProject, getDocument(), myRestrictRange, markers, getId(), myHighlightingSession);
      DaemonCodeAnalyzerEx daemonCodeAnalyzer = DaemonCodeAnalyzerEx.getInstanceEx(myProject);
      FileStatusMap fileStatusMap = daemonCodeAnalyzer.getFileStatusMap();
      fileStatusMap.markFileUpToDate(myDocument, getId());
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  private @NotNull List<LineMarkerInfo<?>> doCollectMarkers() {
    if (!EditorSettingsExternalizable.getInstance().areGutterIconsShown() && !Registry.is("calculate.gutter.actions.always")) {
      // optimization: do not even try to query expensive providers if icons they are going to produce are not to be displayed
      return Collections.emptyList();
    }
    List<LineMarkerInfo<?>> lineMarkers = new ArrayList<>();
    FileViewProvider viewProvider = myFile.getViewProvider();
    int passId = getId();
    for (Language language : viewProvider.getLanguages()) {
      PsiFile root = viewProvider.getPsi(language);
      if (root == null) {
        LOG.error(viewProvider+" for file " +myFile+" returned null root for language "+language+" despite listing it as one of its own languages: "+viewProvider.getLanguages());
        continue;
      }
      HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myProject);
      if (!highlightingLevelManager.shouldHighlight(root)) continue;
      Divider.divideInsideAndOutsideInOneRoot(root, TextRangeScalarUtil.toScalarRange(myRestrictRange), TextRangeScalarUtil.toScalarRange(myPriorityBounds),
           elements -> {
             Collection<LineMarkerProvider> providers = getMarkerProviders(language, myProject);
             List<LineMarkerProvider> providersList = new ArrayList<>(providers);
             queryProviders(
               elements.inside(), root, providersList, (__, info) -> {
                 info.updatePass = passId;
                 lineMarkers.add(info);
                 LineMarkersUtil.addLineMarkerToEditorIncrementally(myProject, getDocument(), info);
               });
             queryProviders(elements.outside(), root, providersList,
               (__, info) -> {
                 info.updatePass = passId;
                 lineMarkers.add(info);
               });
             return true;
           });
    }

    List<LineMarkerInfo<?>> markers = mergeLineMarkers(lineMarkers, getDocument(), passId);
    if (LOG.isDebugEnabled()) {
      LOG.debug("LineMarkersPass.doCollectInformation. lineMarkers: " + lineMarkers+"; merged: "+markers);
    }
    return markers;
  }
  // maintain the `markers` order to show icons in the consistent order
  private static @NotNull List<LineMarkerInfo<?>> mergeLineMarkers(@NotNull List<LineMarkerInfo<?>> markers, @NotNull Document document, int passId) {
    Int2ObjectMap<List<MergeableLineMarkerInfo<?>>> sameLineMarkers = new Int2ObjectLinkedOpenHashMap<>();
    List<LineMarkerInfo<?>> result = new ArrayList<>(markers.size());

    for (LineMarkerInfo<?> marker : markers) {
      if (marker instanceof MergeableLineMarkerInfo<?> mergeable) {
        int line = document.getLineNumber(marker.startOffset);
        List<MergeableLineMarkerInfo<?>> infos = sameLineMarkers.computeIfAbsent(line, __ -> new ArrayList<>());
        infos.add(mergeable);
      }
      else {
        result.add(marker);
      }
    }

    if (sameLineMarkers.isEmpty()) {
      return markers;
    }

    for (List<MergeableLineMarkerInfo<?>> value : sameLineMarkers.values()) {
      result.addAll(MergeableLineMarkerInfo.merge(value, passId));
    }
    return result;
  }

  public static @NotNull List<LineMarkerProvider> getMarkerProviders(@NotNull Language language, @NotNull Project project) {
    List<LineMarkerProvider> forLanguage = LineMarkerProviders.getInstance().allForLanguageOrAny(language);
    List<LineMarkerProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(forLanguage);
    LineMarkerSettings settings = LineMarkerSettings.getSettings();
    return ContainerUtil.filter(providers, provider -> !(provider instanceof LineMarkerProviderDescriptor line)
                                                       || settings.isEnabled(line));
  }

  private void queryProviders(@NotNull List<? extends PsiElement> elements,
                              @NotNull PsiFile containingFile,
                              @NotNull List<? extends LineMarkerProvider> providers,
                              @NotNull PairConsumer<? super PsiElement, ? super LineMarkerInfo<?>> consumer) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    if (myMode != Mode.SLOW) {
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < elements.size(); i++) {
        PsiElement element = elements.get(i);

        //noinspection ForLoopReplaceableByForEach
        for (int j = 0; j < providers.size(); j++) {
          ProgressManager.checkCanceled();
          LineMarkerProvider provider = providers.get(j);
          LineMarkerInfo<?> info;
          try {
            info = provider.getLineMarkerInfo(element);
          }
          catch (ProcessCanceledException | IndexNotReadyException e) {
            throw e;
          }
          catch (Exception e) {
            LOG.error("During querying provider " + provider + " (" + provider.getClass() + ")", e,
                      new Attachment(containingFile.getViewProvider().getVirtualFile().getName(), containingFile.getText()));
            continue;
          }
          if (info != null) {
            if (info.endOffset > getDocument().getTextLength()) {
              Exception exception = new IllegalStateException(provider + " (" + provider.getClass() + ")" +
                          " generated invalid LineMarker " + info + " for element " + element + " (" + element.getClass() + ")." +
                          " document length: " + getDocument().getTextLength());
              LOG.error(PluginException.createByClass(exception, provider.getClass()));
            }
            consumer.consume(element, info);
          }
        }
      }
    }

    if (myMode == Mode.FAST) return;

    if (InjectionUtils.shouldCollectLineMarkersForInjectedFiles(myFile)) {
      Set<PsiFile> visitedInjectedFiles = new HashSet<>();
      // line markers for injected could be slow
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < elements.size(); i++) {
        PsiElement element = elements.get(i);

        queryLineMarkersForInjected(element, containingFile, visitedInjectedFiles, consumer);
      }
    }

    List<LineMarkerInfo<?>> slowLineMarkers = new NotNullList<>();
    //noinspection ForLoopReplaceableByForEach
    for (int j = 0; j < providers.size(); j++) {
      ProgressManager.checkCanceled();
      LineMarkerProvider provider = providers.get(j);
      try {
        provider.collectSlowLineMarkers(elements, slowLineMarkers);
      }
      catch (ProcessCanceledException | IndexNotReadyException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        continue;
      }

      if (!slowLineMarkers.isEmpty()) {
        //noinspection ForLoopReplaceableByForEach
        for (int k = 0; k < slowLineMarkers.size(); k++) {
          LineMarkerInfo<?> slowInfo = slowLineMarkers.get(k);
          PsiElement element = slowInfo.getElement();
          consumer.consume(element, slowInfo);
        }
        slowLineMarkers.clear();
      }
    }
  }

  private void queryLineMarkersForInjected(@NotNull PsiElement element,
                                           @NotNull PsiFile containingFile,
                                           @NotNull Set<? super PsiFile> visitedInjectedFiles,
                                           @NotNull PairConsumer<? super PsiElement, ? super LineMarkerInfo<?>> consumer) {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(containingFile.getProject());
    if (manager.isInjectedFragment(containingFile)) return;

    InjectedLanguageManager.getInstance(containingFile.getProject()).enumerateEx(element, containingFile, false, (injectedPsi, places) -> {
      if (!visitedInjectedFiles.add(injectedPsi)) return; // there may be several concatenated literals making the one injected file
      Project project = injectedPsi.getProject();
      Document document = PsiDocumentManager.getInstance(project).getCachedDocument(injectedPsi);
      if (!(document instanceof DocumentWindow)) return;
      List<PsiElement> injElements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      List<LineMarkerProvider> providers = getMarkerProviders(injectedPsi.getLanguage(), project);

      queryProviders(injElements, injectedPsi, providers, (injectedElement, injectedMarkerInfo) -> {
        GutterIconRenderer gutterRenderer = injectedMarkerInfo.createGutterRenderer();
        TextRange injectedRange = new TextRange(injectedMarkerInfo.startOffset, injectedMarkerInfo.endOffset);
        List<TextRange> editables = manager.intersectWithAllEditableFragments(injectedPsi, injectedRange);
        for (TextRange editable : editables) {
          TextRange hostRange = manager.injectedToHost(injectedPsi, editable);
          Icon icon = gutterRenderer == null ? null : gutterRenderer.getIcon();
          //noinspection unchecked
          GutterIconNavigationHandler<PsiElement> navigationHandler = (GutterIconNavigationHandler<PsiElement>)injectedMarkerInfo.getNavigationHandler();
          LineMarkerInfo<PsiElement> converted = icon == null
                                                 ? new LineMarkerInfo<>(injectedElement, hostRange)
                                                 : new LineMarkerInfo<>(injectedElement, hostRange, icon,
                                                                        e -> injectedMarkerInfo.getLineMarkerTooltip(),
                                                                        navigationHandler, GutterIconRenderer.Alignment.RIGHT,
                                                                        () -> gutterRenderer.getAccessibleName());
          consumer.consume(injectedElement, converted);
        }
      });
    });
  }

  public static @NotNull Collection<LineMarkerInfo<?>> queryLineMarkers(@NotNull PsiFile file, @NotNull Document document) {
    if (file.getNode() == null) {
      // binary file? see IDEADEV-2809
      return Collections.emptyList();
    }
    LineMarkersPass pass = new LineMarkersPass(file.getProject(), file, document, file.getTextRange(), file.getTextRange(), Mode.ALL,
                                               HighlightingSessionImpl.getFromCurrentIndicator(file));
    return pass.doCollectMarkers();
  }

  public static @NotNull LineMarkerInfo<PsiElement> createMethodSeparatorLineMarker(@NotNull PsiElement startFrom, @NotNull EditorColorsManager colorsManager) {
    LineMarkerInfo<PsiElement> info = new LineMarkerInfo<>(startFrom, startFrom.getTextRange());
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    info.separatorPlacement = SeparatorPlacement.TOP;
    return info;
  }

  @Override
  public String toString() {
    return super.toString() + "; myBounds: " + myPriorityBounds;
  }

  enum Mode {
    NONE,
    /**
     * To constraint collection of <code>{@link LineMarkerInfo}</code>s to only <code>{@link LineMarkerProvider#getLineMarkerInfo(PsiElement)}</code>.
     */
    FAST,
    /**
     * To constraint collection of <code>{@link LineMarkerInfo}</code>s to only for injected languages and <code>{@link LineMarkerProvider#collectSlowLineMarkers(List, Collection)}</code>.
     */
    SLOW,
    /**
     * No any constraints, collect all <code>{@link LineMarkerInfo}</code>s
     */
    ALL
  }
}
