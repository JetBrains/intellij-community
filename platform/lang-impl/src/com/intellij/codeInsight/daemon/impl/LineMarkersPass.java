/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.MergeableLineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class LineMarkersPass extends TextEditorHighlightingPass implements LineMarkersProcessor, DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LineMarkersPass");

  private volatile Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  @NotNull private final PsiFile myFile;
  @Nullable private final Editor myEditor;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;

  public LineMarkersPass(@NotNull Project project,
                         @NotNull PsiFile file,
                         @Nullable Editor editor,
                         @NotNull Document document,
                         int startOffset,
                         int endOffset,
                         boolean updateAll) {
    super(project, document, false);
    myFile = file;
    myEditor = editor;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
  }

  @Override
  public void doApplyInformationToEditor() {
    try {
      LineMarkersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, Pass.UPDATE_ALL);
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    final List<LineMarkerInfo> lineMarkers = new ArrayList<LineMarkerInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightingLevelManager.getInstance(myProject).shouldHighlight(psiRoot)) continue;
      //long time = System.currentTimeMillis();
      int start = myStartOffset;
      int end = myEndOffset;
      //if (myEditor != null) {
      //  final int startLine = myEditor.offsetToLogicalPosition(start).line;
      //  final int endLine = myEditor.offsetToLogicalPosition(end).line;
      //  if (startLine != endLine) {
      //    start = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(startLine, 0)));
      //    end = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(endLine + 1, 0))) - 1;
      //  }
      //}
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, start, end);
      if (elements.isEmpty()) {
        elements = Collections.singletonList(psiRoot);
      }
      final List<LineMarkerProvider> providers = getMarkerProviders(language, myProject);
      addLineMarkers(elements, providers, lineMarkers, progress);
      collectLineMarkersForInjected(lineMarkers, elements, this, myFile, progress);
    }

    myMarkers = mergeLineMarkers(lineMarkers);
  }

  private List<LineMarkerInfo> mergeLineMarkers(@NotNull List<LineMarkerInfo> markers) {
    List<MergeableLineMarkerInfo> forMerge = new ArrayList<MergeableLineMarkerInfo>();
    final Iterator<LineMarkerInfo> iterator = markers.iterator();
    while (iterator.hasNext()) {
      final LineMarkerInfo marker = iterator.next();
          
      if (marker instanceof MergeableLineMarkerInfo) {
        iterator.remove();
        forMerge.add((MergeableLineMarkerInfo)marker);
      }
    }

    if (forMerge.isEmpty() || myEditor == null) return markers;

    final List<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>(markers);
    TIntObjectHashMap<List<MergeableLineMarkerInfo>> sameLineMarkers = new TIntObjectHashMap<List<MergeableLineMarkerInfo>>();
    for (MergeableLineMarkerInfo info : forMerge) {
      final LogicalPosition position = myEditor.offsetToLogicalPosition(info.startOffset);
      List<MergeableLineMarkerInfo> infos = sameLineMarkers.get(position.line);
      if (infos == null) {
        infos = new ArrayList<MergeableLineMarkerInfo>();
        sameLineMarkers.put(position.line, infos);
      }
      infos.add(info);
    }

    for (Object v : sameLineMarkers.getValues()) {
      List<MergeableLineMarkerInfo> infos = (List<MergeableLineMarkerInfo>)v;
      result.addAll(MergeableLineMarkerInfo.merge(infos));
    }

    return result;
  }

  public static List<LineMarkerProvider> getMarkerProviders(@NotNull Language language, @NotNull Project project) {
    return DumbService.getInstance(project).filterByDumbAwareness(LineMarkerProviders.INSTANCE.allForLanguage(language));
  }

  @Override
  public void addLineMarkers(@NotNull List<PsiElement> elements,
                             @NotNull final List<LineMarkerProvider> providers,
                             @NotNull final List<LineMarkerInfo> result,
                             @NotNull ProgressIndicator progress) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, elementsSize = elements.size(); i < elementsSize; i++) {
      PsiElement element = elements.get(i);
      progress.checkCanceled();

      //noinspection ForLoopReplaceableByForEach
      for (int j = 0, providersSize = providers.size(); j < providersSize; j++) {
        LineMarkerProvider provider = providers.get(j);
        LineMarkerInfo info;
        try {
          info = provider.getLineMarkerInfo(element);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (IndexNotReadyException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          continue;
        }
        if (info != null) {
          result.add(info);
        }
      }
    }
  }

  public static void collectLineMarkersForInjected(@NotNull final List<LineMarkerInfo> result,
                                                   @NotNull List<PsiElement> elements,
                                                   @NotNull final LineMarkersProcessor processor,
                                                   @NotNull PsiFile file,
                                                   @NotNull final ProgressIndicator progress) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
    final List<LineMarkerInfo> injectedMarkers = new ArrayList<LineMarkerInfo>();

    final Set<PsiFile> injectedFiles = new THashSet<PsiFile>();
    final PsiLanguageInjectionHost.InjectedPsiVisitor collectingVisitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull final PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        injectedFiles.add(injectedPsi);
      }
    };
    for (int i = 0, size = elements.size(); i < size; ++i) {
      InjectedLanguageUtil.enumerate(elements.get(i), file, false, collectingVisitor);
    }
    for (PsiFile injectedPsi : injectedFiles) {
      final Project project = injectedPsi.getProject();
      Document document = PsiDocumentManager.getInstance(project).getCachedDocument(injectedPsi);
      if (!(document instanceof DocumentWindow)) return;
      List<PsiElement> injElements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      final List<LineMarkerProvider> providers = getMarkerProviders(injectedPsi.getLanguage(), project);
      processor.addLineMarkers(injElements, providers, injectedMarkers, progress);
      for (final LineMarkerInfo<PsiElement> injectedMarker : injectedMarkers) {
        GutterIconRenderer gutterRenderer = injectedMarker.createGutterRenderer();
        TextRange injectedRange = new TextRange(injectedMarker.startOffset, injectedMarker.endOffset);
        List<TextRange> editables = manager.intersectWithAllEditableFragments(injectedPsi, injectedRange);
        for (TextRange editable : editables) {
          TextRange hostRange = manager.injectedToHost(injectedPsi, editable);
          Icon icon = gutterRenderer == null ? null : gutterRenderer.getIcon();
          LineMarkerInfo converted =
              new LineMarkerInfo<PsiElement>(injectedMarker.getElement(), hostRange, icon, injectedMarker.updatePass,
                                 new Function<PsiElement, String>() {
                                   @Override
                                   public String fun(PsiElement element) {
                                     return injectedMarker.getLineMarkerTooltip();
                                   }
                                 }, injectedMarker.getNavigationHandler(), GutterIconRenderer.Alignment.RIGHT);
          result.add(converted);
        }
      }
      injectedMarkers.clear();
    }
  }

  @NotNull
  public Collection<LineMarkerInfo> queryLineMarkers() {
    if (myFile.getNode() == null) {
      // binary file? see IDEADEV-2809
      return Collections.emptyList();
    }
    doCollectInformation(new EmptyProgressIndicator());
    return myMarkers;
  }

  @NotNull
  public static LineMarkerInfo createMethodSeparatorLineMarker(@NotNull PsiElement startFrom, @NotNull EditorColorsManager colorsManager) {
    LineMarkerInfo info = new LineMarkerInfo<PsiElement>(
      startFrom, 
      startFrom.getTextRange(), 
      null, 
      Pass.UPDATE_ALL, 
      FunctionUtil.<Object, String>nullConstant(),
      null, 
      GutterIconRenderer.Alignment.RIGHT
    );
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    info.separatorColor = scheme.getColor(CodeInsightColors.METHOD_SEPARATORS_COLOR);
    info.separatorPlacement = SeparatorPlacement.TOP;
    return info;
  }
}
