/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageManagerImpl;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
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
  @NotNull private final TextRange myBounds;

  LineMarkersPass(@NotNull Project project,
                  @NotNull PsiFile file,
                  @Nullable Editor editor,
                  @NotNull Document document,
                  @NotNull TextRange bounds) {
    super(project, document, false);
    myFile = file;
    myEditor = editor;
    myBounds = bounds;
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
      LineMarkersUtil.setLineMarkersToEditor(myProject, getDocument(), myBounds, myMarkers, Pass.UPDATE_ALL);
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
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myBounds.getStartOffset(), myBounds.getEndOffset());
      if (elements.isEmpty()) {
        elements = Collections.singletonList(psiRoot);
      }
      final List<LineMarkerProvider> providers = getMarkerProviders(language, myProject);
      addLineMarkers(elements, providers, lineMarkers, progress);
      collectLineMarkersForInjected(lineMarkers, elements, this, myFile, progress);
    }

    myMarkers = mergeLineMarkers(lineMarkers, myEditor);
  }

  static List<LineMarkerInfo> mergeLineMarkers(@NotNull List<LineMarkerInfo> markers, @Nullable Editor editor) {
    List<MergeableLineMarkerInfo> forMerge = new ArrayList<MergeableLineMarkerInfo>();
    final Iterator<LineMarkerInfo> iterator = markers.iterator();
    while (iterator.hasNext()) {
      final LineMarkerInfo marker = iterator.next();
          
      if (marker instanceof MergeableLineMarkerInfo) {
        iterator.remove();
        forMerge.add((MergeableLineMarkerInfo)marker);
      }
    }

    if (forMerge.isEmpty() || editor == null) return markers;

    final List<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>(markers);
    TIntObjectHashMap<List<MergeableLineMarkerInfo>> sameLineMarkers = new TIntObjectHashMap<List<MergeableLineMarkerInfo>>();
    for (MergeableLineMarkerInfo info : forMerge) {
      int line = editor.getDocument().getLineNumber(info.startOffset);
      List<MergeableLineMarkerInfo> infos = sameLineMarkers.get(line);
      if (infos == null) {
        infos = new ArrayList<MergeableLineMarkerInfo>();
        sameLineMarkers.put(line, infos);
      }
      infos.add(info);
    }

    for (Object v : sameLineMarkers.getValues()) {
      List<MergeableLineMarkerInfo> infos = (List<MergeableLineMarkerInfo>)v;
      result.addAll(MergeableLineMarkerInfo.merge(infos));
    }

    return result;
  }

  public static List<LineMarkerProvider> getMarkerProviders(@NotNull Language language, @NotNull final Project project) {
    List<LineMarkerProvider> forLanguage = LineMarkerProviders.INSTANCE.allForLanguageOrAny(language);
    List<LineMarkerProvider> providers = DumbService.getInstance(project).filterByDumbAwareness(forLanguage);
    final LineMarkerSettings settings = LineMarkerSettings.getSettings();
    return ContainerUtil.filter(providers, new Condition<LineMarkerProvider>() {
      @Override
      public boolean value(LineMarkerProvider provider) {
        if (!(provider instanceof LineMarkerProviderDescriptor)) return true;
        return settings.isEnabled((LineMarkerProviderDescriptor)provider);
      }
    });
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

  static void collectLineMarkersForInjected(@NotNull final List<LineMarkerInfo> result,
                                            @NotNull List<PsiElement> elements,
                                            @NotNull final LineMarkersProcessor processor,
                                            @NotNull final PsiFile file,
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
    InjectedLanguageManagerImpl.getInstanceImpl(file.getProject()).processInjectableElements(elements, new Processor<PsiElement>() {
      @Override
      public boolean process(PsiElement element) {
        InjectedLanguageUtil.enumerate(element, file, false, collectingVisitor);
        return true;
      }
    });
    for (PsiFile injectedPsi : injectedFiles) {
      final Project project = injectedPsi.getProject();
      Document document = PsiDocumentManager.getInstance(project).getCachedDocument(injectedPsi);
      if (!(document instanceof DocumentWindow)) return;
      List<PsiElement> injElements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
      final List<LineMarkerProvider> providers = getMarkerProviders(injectedPsi.getLanguage(), project);
      processor.addLineMarkers(injElements, providers, injectedMarkers, progress);
      for (final LineMarkerInfo injectedMarker : injectedMarkers) {
        GutterIconRenderer gutterRenderer = injectedMarker.createGutterRenderer();
        TextRange injectedRange = new TextRange(injectedMarker.startOffset, injectedMarker.endOffset);
        List<TextRange> editables = manager.intersectWithAllEditableFragments(injectedPsi, injectedRange);
        for (TextRange editable : editables) {
          TextRange hostRange = manager.injectedToHost(injectedPsi, editable);
          Icon icon = gutterRenderer == null ? null : gutterRenderer.getIcon();
          LineMarkerInfo<PsiElement> converted =
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
  public static Collection<LineMarkerInfo> queryLineMarkers(@NotNull PsiFile file, @NotNull Document document) {
    if (file.getNode() == null) {
      // binary file? see IDEADEV-2809
      return Collections.emptyList();
    }
    LineMarkersPass pass = new LineMarkersPass(file.getProject(), file, null, document, file.getTextRange());
    pass.doCollectInformation(new EmptyProgressIndicator());
    return pass.myMarkers;
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
