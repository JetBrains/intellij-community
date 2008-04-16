/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LineMarkersPass extends ProgressableTextEditorHighlightingPass {
  private volatile Collection<LineMarkerInfo> myMarkers = Collections.emptyList();

  private final PsiFile myFile;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myUpdateAll;

  public LineMarkersPass(@NotNull Project project,
                         @NotNull PsiFile file,
                         @NotNull Document document,
                         int startOffset,
                         int endOffset,
                         boolean updateAll) {
    super(project, document, GeneralHighlightingPass.IN_PROGRESS_ICON, GeneralHighlightingPass.PRESENTABLE_NAME);
    myFile = file;
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
      final LineMarkerProvider provider = LineMarkerProviders.INSTANCE.forLanguage(language);
      if (provider != null) {
        addLineMarkers(elements, provider, lineMarkers);
      }
    }

    myMarkers = lineMarkers;
  }

  private static void addLineMarkers(List<PsiElement> elements, final LineMarkerProvider provider, List<LineMarkerInfo> result) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      LineMarkerInfo info = provider.getLineMarkerInfo(element);
      if (info != null) {
        result.add(info);
      }
    }
  }

  public Collection<LineMarkerInfo> queryLineMarkers() {
    try {
      if (myFile.getNode() == null) {
        // binary file? see IDEADEV-2809
        return Collections.emptyList();
      }
      ArrayList<LineMarkerInfo> result = new ArrayList<LineMarkerInfo>();
      final LineMarkerProvider provider = LineMarkerProviders.INSTANCE.forLanguage(myFile.getLanguage());
      if (provider != null) {
        addLineMarkers(CollectHighlightsUtil.getElementsInRange(myFile, myStartOffset, myEndOffset), provider, result);
      }
      return result;
    }
    catch (ProcessCanceledException e) {
      return null;
    }
  }

  public double getProgress() {
    // do not show progress of visible highlighters update
    return myUpdateAll ? super.getProgress() : -1;
  }
}