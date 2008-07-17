/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.Function;
import com.intellij.injected.editor.DocumentWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.*;

public class LineMarkersPass extends ProgressableTextEditorHighlightingPass implements LineMarkersProcessor {
  private volatile Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;

  public LineMarkersPass(@NotNull Project project,
                         @NotNull PsiFile file,
                         @NotNull Document document,
                         int startOffset,
                         int endOffset,
                         boolean updateAll) {
    super(project, document, GeneralHighlightingPass.IN_PROGRESS_ICON, GeneralHighlightingPass.PRESENTABLE_NAME, file);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myUpdateAll = updateAll;
  }

  protected void applyInformationWithProgress() {
    UpdateHighlightersUtil.setLineMarkersToEditor(myProject, myDocument, myStartOffset, myEndOffset, myMarkers, Pass.UPDATE_ALL);
  }

  protected void collectInformationWithProgress(final ProgressIndicator progress) {
    final List<LineMarkerInfo> lineMarkers = new ArrayList<LineMarkerInfo>();
    final FileViewProvider viewProvider = myFile.getViewProvider();
    final Set<Language> relevantLanguages = viewProvider.getLanguages();
    for (Language language : relevantLanguages) {
      PsiElement psiRoot = viewProvider.getPsi(language);
      if (!HighlightLevelUtil.shouldHighlight(psiRoot)) continue;
      //long time = System.currentTimeMillis();
      List<PsiElement> elements = CollectHighlightsUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      if (elements.isEmpty()) {
        elements = Collections.singletonList(psiRoot);
      }
      final List<LineMarkerProvider> providers = LineMarkerProviders.INSTANCE.allForLanguage(language);
      addLineMarkers(elements, providers, lineMarkers);
      collectLineMarkersForInjected(lineMarkers, InjectedLanguageManager.getInstance(myProject), elements, this, myFile);
    }

    myMarkers = lineMarkers;
  }

  public void addLineMarkers(List<PsiElement> elements, final List<LineMarkerProvider> providers, final List<LineMarkerInfo> result) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      for (LineMarkerProvider provider: providers) {
        LineMarkerInfo info = provider.getLineMarkerInfo(element);
        if (info != null) {
          result.add(info);
        }
      }
    }
  }

  public static void collectLineMarkersForInjected(final List<LineMarkerInfo> result, final InjectedLanguageManager manager, List<PsiElement> elements,
                                                   final LineMarkersProcessor processor,
                                                   PsiFile file) {
    final List<LineMarkerInfo> injectedMarkers = new ArrayList<LineMarkerInfo>();
    for (PsiElement element : elements) {
      InjectedLanguageUtil.enumerate(element, file, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        public void visit(@NotNull final PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          List<PsiElement> injElements = CollectHighlightsUtil.getElementsInRange(injectedPsi, 0, injectedPsi.getTextLength());
          final List<LineMarkerProvider> providers = LineMarkerProviders.INSTANCE.allForLanguage(injectedPsi.getLanguage());
          processor.addLineMarkers(injElements, providers, injectedMarkers);
          Document document = PsiDocumentManager.getInstance(injectedPsi.getProject()).getCachedDocument(injectedPsi);
          if (!(document instanceof DocumentWindow)) return;
          DocumentWindow injectedDocument = (DocumentWindow)document;
          for (final LineMarkerInfo injectedMarker : injectedMarkers) {
            GutterIconRenderer gutterRenderer = injectedMarker.createGutterRenderer();
            TextRange injectedRange = new TextRange(injectedMarker.startOffset, injectedMarker.endOffset);

            TextRange editable = injectedDocument.intersectWithEditable(injectedRange);
            if (editable == null) continue;
            TextRange hostRange = manager.injectedToHost(injectedPsi, editable);
            Icon icon = gutterRenderer == null ? null : gutterRenderer.getIcon();
            LineMarkerInfo converted =
                new LineMarkerInfo(injectedMarker.getElement(), hostRange.getStartOffset(), icon, injectedMarker.updatePass, new Function<PsiElement, String>() {
                  public String fun(PsiElement element) {
                    return injectedMarker.getLineMarkerTooltip();
                  }
                }, injectedMarker.getNavigationHandler());
            converted.endOffset = hostRange.getEndOffset();
            result.add(converted);
          }
          injectedMarkers.clear();
        }
      }, false);
    }
  }

  public Collection<LineMarkerInfo> queryLineMarkers() {
    if (myFile.getNode() == null) {
      // binary file? see IDEADEV-2809
      return Collections.emptyList();
    }
    collectInformationWithProgress(null);
    return myMarkers;
  }

  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }

  @TestOnly
  public Collection<LineMarkerInfo> getMarkers() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    return myMarkers;
  }
}