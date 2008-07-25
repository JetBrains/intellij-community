package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

import javax.swing.*;
import java.util.Arrays;

public abstract class GotoTargetHandler implements CodeInsightActionHandler {
  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(getFeatureUsedKey());

    Pair<PsiElement, PsiElement[]> sourceAndTarget = getSourceAndTargetElements(editor, file);
    show(project, editor, file, sourceAndTarget.first, sourceAndTarget.second);
  }

  protected abstract String getFeatureUsedKey();

  protected abstract Pair<PsiElement, PsiElement[]> getSourceAndTargetElements(Editor editor, PsiFile file);

  private void show(Project project, Editor editor, PsiFile file, final PsiElement sourceElement, final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
      handleNoVariansCase(project, editor, file);
      return;
    }

    if (elements.length == 1) {
      Navigatable descriptor = EditSourceUtil.getDescriptor(elements[0]);
      if (descriptor != null && descriptor.canNavigate()) {
        descriptor.navigate(true);
      }
    }
    else {
      PsiElementListCellRenderer renderer = null;
      for (GotoTargetRendererProvider provider : Extensions.getExtensions(GotoTargetRendererProvider.EP_NAME)) {
        renderer = provider.getRenderer(elements);
        if (renderer != null) break;
      }

      String titleKey;
      if (renderer == null) {
        renderer = new DefaultPsiElementListCellRenderer();
        titleKey = getChooserInFileTitleKey(sourceElement);
      } else {
        titleKey = getChooserTitleKey(sourceElement);
      }
      String name = ((PsiNamedElement)sourceElement).getName();
      String title = CodeInsightBundle.message(titleKey, name, elements.length);

      if (shouldSortResult()) Arrays.sort(elements, renderer.getComparator());

      final JList list = new JList(elements);
      list.setCellRenderer(renderer);

      renderer.installSpeedSearch(list);

      final Runnable runnable = new Runnable() {
        public void run() {
          int[] ids = list.getSelectedIndices();
          if (ids == null || ids.length == 0) return;
          Object[] selectedElements = list.getSelectedValues();
          for (Object element : selectedElements) {
            Navigatable descriptor = EditSourceUtil.getDescriptor((PsiElement)element);
            if (descriptor != null && descriptor.canNavigate()) {
              descriptor.navigate(true);
            }
          }
        }
      };

      new PopupChooserBuilder(list).
          setTitle(title).
          setItemChoosenCallback(runnable).
          createPopup().showInBestPositionFor(editor);
    }
  }

  protected boolean shouldSortResult() {
    return true;
  }

  protected void handleNoVariansCase(Project project, Editor editor, PsiFile file) {
  }

  protected abstract String getChooserInFileTitleKey(PsiElement sourceElement);

  protected abstract String getChooserTitleKey(PsiElement sourceElement);

  private static class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer {
    public String getElementText(final PsiElement element) {
      return element.getContainingFile().getName();
    }

    protected String getContainerText(final PsiElement element, final String name) {
      return null;
    }

    protected int getIconFlags() {
      return 0;
    }
  }
}
