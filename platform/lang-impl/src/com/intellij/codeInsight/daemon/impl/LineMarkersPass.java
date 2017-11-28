/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.daemon.*;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.NotNullList;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class LineMarkersPass extends TextEditorHighlightingPass implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LineMarkersPass");

  private volatile List<LineMarkerInfo> myMarkers = Collections.emptyList();

  @NotNull private final PsiFile myFile;
  @NotNull private final TextRange myPriorityBounds;
  @NotNull private final TextRange myRestrictRange;

  LineMarkersPass(@NotNull Project project,
                  @NotNull PsiFile file,
                  @NotNull Document document,
                  @NotNull TextRange priorityBounds,
                  @NotNull TextRange restrictRange) {
    super(project, document, false);
    myFile = file;
    myPriorityBounds = priorityBounds;
    myRestrictRange = restrictRange;
  }

  @NotNull
  @Override
  public Document getDocument() {
    //noinspection ConstantConditions
    return super.getDocument();
  }

  @Override
  public void doApplyInformationToEditor() {
    try {
      LineMarkersUtil.setLineMarkersToEditor(myProject, getDocument(), myRestrictRange, myMarkers, getId());
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    final List<LineMarkerInfo> lineMarkers = new ArrayList<>();
    FileViewProvider viewProvider = myFile.getViewProvider();
    for (Language language : viewProvider.getLanguages()) {
      final PsiFile root = viewProvider.getPsi(language);
      HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myProject);
      if (!highlightingLevelManager.shouldHighlight(root)) continue;
      Divider.divideInsideAndOutsideInOneRoot(root, myRestrictRange, myPriorityBounds,
           elements -> {
             Collection<LineMarkerProvider> providers = getMarkerProviders(language, myProject);
             List<LineMarkerProvider> providersList = new ArrayList<>(providers);

             queryProviders(elements.inside, root, providersList, (element, info) -> {
               lineMarkers.add(info);
               ApplicationManager.getApplication().invokeLater(() -> {
                 if (isValid()) {
                   LineMarkersUtil.addLineMarkerToEditorIncrementally(myProject, getDocument(), info);
                 }
               }, myProject.getDisposed());
             });
             queryProviders(elements.outside, root, providersList, (element, info) -> lineMarkers.add(info));
             return true;
           });
    }

    myMarkers = mergeLineMarkers(lineMarkers, getDocument());
    if (LOG.isDebugEnabled()) {
      LOG.debug("LineMarkersPass.doCollectInformation. lineMarkers: " + lineMarkers+"; merged: "+myMarkers);
    }
  }

  @NotNull
  private static List<LineMarkerInfo> mergeLineMarkers(@NotNull List<LineMarkerInfo> markers, @NotNull Document document) {
    List<MergeableLineMarkerInfo> forMerge = new ArrayList<>();
    TIntObjectHashMap<List<MergeableLineMarkerInfo>> sameLineMarkers = new TIntObjectHashMap<>();

    for (int i = markers.size() - 1; i >= 0; i--) {
      LineMarkerInfo marker = markers.get(i);
      if (marker instanceof MergeableLineMarkerInfo) {
        MergeableLineMarkerInfo mergeable = (MergeableLineMarkerInfo)marker;
        forMerge.add(mergeable);
        markers.remove(i);

        int line = document.getLineNumber(marker.startOffset);
        List<MergeableLineMarkerInfo> infos = sameLineMarkers.get(line);
        if (infos == null) {
          infos = new ArrayList<>();
          sameLineMarkers.put(line, infos);
        }
        infos.add(mergeable);
      }
    }

    if (forMerge.isEmpty()) return markers;

    List<LineMarkerInfo> result = new ArrayList<>(markers);

    for (Object v : sameLineMarkers.getValues()) {
      List<MergeableLineMarkerInfo> infos = (List<MergeableLineMarkerInfo>)v;
      result.addAll(MergeableLineMarkerInfo.merge(infos));
    }

    return result;
  }

  @NotNull
  public static List<LineMarkerProvider> getMarkerProviders(@NotNull Language language, @NotNull final Project project) {
    List<LineMarkerProvider> forLanguage = LineMarkerProviders.INSTANCE.allForLanguageOrAny(language);
    List<LineMarkerProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(forLanguage);
    final LineMarkerSettings settings = LineMarkerSettings.getSettings();
    return ContainerUtil.filter(providers, provider -> !(provider instanceof LineMarkerProviderDescriptor)
                                                       || settings.isEnabled((LineMarkerProviderDescriptor)provider));
  }

  private static void queryProviders(@NotNull List<PsiElement> elements,
                                     @NotNull PsiFile containingFile,
                                     @NotNull List<LineMarkerProvider> providers,
                                     @NotNull PairConsumer<PsiElement, LineMarkerInfo> consumer) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    Set<PsiFile> visitedInjectedFiles = new THashSet<>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < elements.size(); i++) {
      PsiElement element = elements.get(i);

      //noinspection ForLoopReplaceableByForEach
      for (int j = 0; j < providers.size(); j++) {
        ProgressManager.checkCanceled();
        LineMarkerProvider provider = providers.get(j);
        LineMarkerInfo info;
        try {
          info = provider.getLineMarkerInfo(element);
        }
        catch (ProcessCanceledException | IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          continue;
        }
        if (info != null) {
          consumer.consume(element, info);
        }
      }

      queryLineMarkersForInjected(element, containingFile, visitedInjectedFiles, consumer);
    }

    List<LineMarkerInfo> slowLineMarkers = new NotNullList<>();
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
          LineMarkerInfo slowInfo = slowLineMarkers.get(k);
          PsiElement element = slowInfo.getElement();
          consumer.consume(element, slowInfo);
        }
        slowLineMarkers.clear();
      }
    }
  }

  private static void queryLineMarkersForInjected(@NotNull PsiElement element,
                                                  @NotNull final PsiFile containingFile,
                                                  @NotNull Set<PsiFile> visitedInjectedFiles,
                                                  @NotNull final PairConsumer<PsiElement, LineMarkerInfo> consumer) {
    if (containingFile.getViewProvider() instanceof InjectedFileViewProvider) return;
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(containingFile.getProject());

    InjectedLanguageUtil.enumerate(element, containingFile, false, (injectedPsi, places) -> {
      if (!visitedInjectedFiles.add(injectedPsi)) return; // there may be several concatenated literals making the one injected file
      final Project project = injectedPsi.getProject();
      Document document = PsiDocumentManager.getInstance(project).getCachedDocument(injectedPsi);
      if (!(document instanceof DocumentWindow)) return;
      List<PsiElement> injElements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      final List<LineMarkerProvider> providers = getMarkerProviders(injectedPsi.getLanguage(), project);

      queryProviders(injElements, injectedPsi, providers, (injectedElement, injectedMarker) -> {
        GutterIconRenderer gutterRenderer = injectedMarker.createGutterRenderer();
        TextRange injectedRange = new TextRange(injectedMarker.startOffset, injectedMarker.endOffset);
        List<TextRange> editables = manager.intersectWithAllEditableFragments(injectedPsi, injectedRange);
        for (TextRange editable : editables) {
          TextRange hostRange = manager.injectedToHost(injectedPsi, editable);
          Icon icon = gutterRenderer == null ? null : gutterRenderer.getIcon();
          GutterIconNavigationHandler<PsiElement> navigationHandler = injectedMarker.getNavigationHandler();
          LineMarkerInfo<PsiElement> converted =
            new LineMarkerInfo<>(injectedElement, hostRange, icon, injectedMarker.updatePass,
                                 e -> injectedMarker.getLineMarkerTooltip(), navigationHandler,
                                 GutterIconRenderer.Alignment.RIGHT);
          consumer.consume(injectedElement, converted);
        }
      });
    });
  }

  @NotNull
  public static Collection<LineMarkerInfo> queryLineMarkers(@NotNull PsiFile file, @NotNull Document document) {
    if (file.getNode() == null) {
      // binary file? see IDEADEV-2809
      return Collections.emptyList();
    }
    LineMarkersPass pass = new LineMarkersPass(file.getProject(), file, document, file.getTextRange(), file.getTextRange());
    pass.doCollectInformation(new EmptyProgressIndicator());
    return pass.myMarkers;
  }

  @NotNull
  public static LineMarkerInfo createMethodSeparatorLineMarker(@NotNull PsiElement startFrom, @NotNull EditorColorsManager colorsManager) {
    LineMarkerInfo info = new LineMarkerInfo<>(
      startFrom,
      startFrom.getTextRange(),
      null,
      Pass.LINE_MARKERS,
      FunctionUtil.<Object, String>nullConstant(),
      null,
      GutterIconRenderer.Alignment.RIGHT
    );
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    info.separatorPlacement = SeparatorPlacement.TOP;
    return info;
  }

  @Override
  public String toString() {
    return super.toString() + "; myBounds: " + myPriorityBounds;
  }
}
