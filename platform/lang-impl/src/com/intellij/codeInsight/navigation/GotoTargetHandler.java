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
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.components.JBList;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class GotoTargetHandler implements CodeInsightActionHandler {
  private PsiElementListCellRenderer myDefaultTargetElementRenderer = new DefaultPsiElementListCellRenderer();
  private DefaultListCellRenderer myActionElementRenderer = new ActionCellRenderer();

  public boolean startInWriteAction() {
    return false;
  }

  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(getFeatureUsedKey());

    try {
      GotoData gotoData = getSourceAndTargetElements(editor, file);
      if (gotoData.source != null) {
        show(project, editor, file, gotoData.source, gotoData.targets, gotoData.additionalActions);
      }
    }
    catch (IndexNotReadyException e) {
      DumbService.getInstance(project).showDumbModeNotification("Navigation is not available here during index update");
    }
  }

  @NonNls
  protected abstract String getFeatureUsedKey();

  @Nullable
  protected abstract GotoData getSourceAndTargetElements(Editor editor, PsiFile file);

  private void show(Project project,
                    Editor editor,
                    PsiFile file,
                    final PsiElement sourceElement,
                    final PsiElement[] targets,
                    final List<AdditionalAction> additionalActions) {
    if (targets.length == 0 && additionalActions.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, getNotFoundMessage(project, editor, file));
      return;
    }

    if (targets.length == 1 && additionalActions.isEmpty()) {
      Navigatable descriptor = targets[0] instanceof Navigatable ? (Navigatable)targets[0] : EditSourceUtil.getDescriptor(targets[0]);
      if (descriptor != null && descriptor.canNavigate()) {
        navigateToElement(descriptor);
      }
      return;
    }

    final Map<Object, PsiElementListCellRenderer> targetsWithRenderers = new THashMap<Object, PsiElementListCellRenderer>(targets.length);

    GotoTargetRendererProvider[] providers = Extensions.getExtensions(GotoTargetRendererProvider.EP_NAME);

    for (PsiElement eachTarget : targets) {
      PsiElementListCellRenderer renderer = null;
      for (GotoTargetRendererProvider eachProvider : providers) {
        renderer = eachProvider.getRenderer(eachTarget);
        if (renderer != null) break;
      }
      if (renderer == null) {
        renderer = myDefaultTargetElementRenderer;
      }
      targetsWithRenderers.put(eachTarget, renderer);
    }


    String name = ((PsiNamedElement)sourceElement).getName();
    String title = getChooserTitle(sourceElement, name, targets.length);

    if (shouldSortTargets()) {
      Arrays.sort(targets, new Comparator<PsiElement>() {
        @Override
        public int compare(PsiElement o1, PsiElement o2) {
          return getComparingObject(o1).compareTo(getComparingObject(o2));
        }

        private Comparable getComparingObject(PsiElement o1) {
          return targetsWithRenderers.get(o1).getComparingObject(o1);
        }
      });
    }

    List<Object> allElements = new ArrayList<Object>(targets.length + additionalActions.size());
    Collections.addAll(allElements, targets);
    allElements.addAll(additionalActions);

    final JList list = new JBList(allElements);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof AdditionalAction) {
          return myActionElementRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
        return targetsWithRenderers.get(value).getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    final Runnable runnable = new Runnable() {
      public void run() {
        int[] ids = list.getSelectedIndices();
        if (ids == null || ids.length == 0) return;
        Object[] selectedElements = list.getSelectedValues();
        for (Object element : selectedElements) {
          if (element instanceof AdditionalAction) {
            ((AdditionalAction)element).execute();
          }
          else {
            Navigatable nav = element instanceof Navigatable ? (Navigatable)element : EditSourceUtil.getDescriptor((PsiElement)element);
            if (nav != null && nav.canNavigate()) {
              navigateToElement(nav);
            }
          }
        }
      }
    };

    final PopupChooserBuilder builder = new PopupChooserBuilder(list);
    builder.setFilteringEnabled(new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        if (o instanceof AdditionalAction) {
          return ((AdditionalAction)o).getText();
        }
        return targetsWithRenderers.get(o).getElementText((PsiElement)o);
      }
    });

    builder.
      setTitle(title).
      setItemChoosenCallback(runnable).
      setMovable(true).
      createPopup().showInBestPositionFor(editor);
  }


  protected void navigateToElement(Navigatable descriptor) {
    descriptor.navigate(true);
  }

  protected boolean shouldSortTargets() {
    return true;
  }

  protected abstract String getChooserTitle(PsiElement sourceElement, String name, int length);

  protected abstract String getNotFoundMessage(Project project, Editor editor, PsiFile file);

  public interface AdditionalAction {
    String getText();

    Icon getIcon();

    void execute();
  }

  public static class GotoData {
    public final PsiElement source;
    public final PsiElement[] targets;
    public final List<AdditionalAction> additionalActions;

    public GotoData(PsiElement source, PsiElement[] targets, List<AdditionalAction> additionalActions) {
      this.source = source;
      this.targets = targets;
      this.additionalActions = additionalActions;
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

  private class ActionCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value != null) {
        AdditionalAction action = (AdditionalAction)value;
        setText(action.getText());
        setIcon(action.getIcon());
      }
      return result;
    }
  }
}
