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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public abstract class GotoTargetHandler implements CodeInsightActionHandler {
  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(getFeatureUsedKey());

    try {
      Pair<PsiElement, PsiElement[]> sourceAndTarget = getSourceAndTargetElements(editor, file);
      if (sourceAndTarget.first != null) {
        show(project, editor, file, sourceAndTarget.first, sourceAndTarget.second);
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification("Navigation is not available here during index update");
    }
  }

  @NonNls
  protected abstract String getFeatureUsedKey();

  protected abstract Pair<PsiElement, PsiElement[]> getSourceAndTargetElements(Editor editor, PsiFile file);

  private void show(Project project, final Editor editor, final PsiFile file, final PsiElement sourceElement, final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
      handleNoVariansCase(project, editor, file);
      return;
    }

    if (elements.length == 1 && elements[0] != null) {
      Navigatable descriptor = elements[0] instanceof Navigatable ? (Navigatable) elements[0] : EditSourceUtil.getDescriptor(elements[0]);
      if (descriptor != null && descriptor.canNavigate()) {
        navigateToElement(descriptor);
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
      String title = CodeInsightBundle.message(titleKey, name, hasNullUsage() ? elements.length - 1 : elements.length);

      if (shouldSortResult()) Arrays.sort(elements, renderer.getComparator());

      final JList list = new JList(elements);
      list.setCellRenderer(renderer);


      final Runnable runnable = new Runnable() {
        public void run() {
          int[] ids = list.getSelectedIndices();
          if (ids == null || ids.length == 0) return;
          Object[] selectedElements = list.getSelectedValues();
          for (Object element : selectedElements) {
            final Navigatable descriptor = element instanceof Navigatable ? (Navigatable) element : EditSourceUtil.getDescriptor((PsiElement)element);
            if (descriptor != null) {
              if (descriptor.canNavigate()) {
                navigateToElement(descriptor);
              }
            }
            else {
              navigateToElement(element, editor, file);
            }
          }
        }
      };

      final PopupChooserBuilder builder = new PopupChooserBuilder(list);
      renderer.installSpeedSearch(builder);
      builder.
          setTitle(title).
          setItemChoosenCallback(runnable).
          setMovable(true).
          createPopup().showInBestPositionFor(editor);
    }
  }

  protected void navigateToElement(@Nullable Object element, @NotNull Editor editor, @NotNull PsiFile file) {
    //special case for null
  }

  protected boolean hasNullUsage() {
    return false;
  }

  protected void navigateToElement(Navigatable descriptor) {
    descriptor.navigate(true);
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
