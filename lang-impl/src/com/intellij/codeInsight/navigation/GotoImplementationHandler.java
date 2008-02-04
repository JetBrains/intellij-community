package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;

import javax.swing.*;
import java.util.Arrays;

public class GotoImplementationHandler implements CodeInsightActionHandler {

  public void invoke(Project project, Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = TargetElementUtilBase.getInstance().findTargetElement(editor, ImplementationSearcher.FLAGS, offset);

    PsiElement[] result = new ImplementationSearcher().searchImplementations(editor, element, offset);
    if (result.length > 0) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.implementation");
      show(editor, element, result);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static void show(Editor editor, final PsiElement sourceElement, final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
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
      GotoImplementationRendererProvider rendererProvider = null;
      for(GotoImplementationRendererProvider provider: Extensions.getExtensions(GotoImplementationRendererProvider.EP_NAME)) {
        renderer = provider.getRenderer(elements);
        if (renderer != null) {
          rendererProvider = provider;
          break;
        }
      }

      if (renderer == null) {
        renderer = new DefaultPsiElementListCellRenderer();
      }

      Arrays.sort(elements, renderer.getComparator());

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

      final String name = ((PsiNamedElement)sourceElement).getName();
      final String title;
      if (rendererProvider != null) {
        title = rendererProvider.getChooserTitle(name, elements);
      }
      else {
        title = CodeInsightBundle.message("goto.implementation.in.file.chooser.title", name, elements.length);
      }
      new PopupChooserBuilder(list).
        setTitle(title).
        setItemChoosenCallback(runnable).
        createPopup().showInBestPositionFor(editor);
    }
  }

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
