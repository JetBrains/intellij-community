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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.find.FindUtil;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.JBListWithHintProvider;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.usages.UsageView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class GotoTargetHandler implements CodeInsightActionHandler {
  private static final PsiElementListCellRenderer ourDefaultTargetElementRenderer = new DefaultPsiElementListCellRenderer();
  private final DefaultListCellRenderer myActionElementRenderer = new ActionCellRenderer();

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(getFeatureUsedKey());

    try {
      GotoData gotoData = getSourceAndTargetElements(editor, file);
      if (gotoData != null && gotoData.source != null) {
        show(project, editor, file, gotoData);
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

  private void show(@NotNull final Project project,
                    @NotNull Editor editor,
                    @NotNull PsiFile file,
                    @NotNull final GotoData gotoData) {
    final PsiElement[] targets = gotoData.targets;
    final List<AdditionalAction> additionalActions = gotoData.additionalActions;

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

    for (PsiElement eachTarget : targets) {
      gotoData.renderers.put(eachTarget, createRenderer(gotoData, eachTarget));
    }

    final String name = ((PsiNamedElement)gotoData.source).getName();
    final String title = getChooserTitle(gotoData.source, name, targets.length);

    if (shouldSortTargets()) {
      Arrays.sort(targets, createComparator(gotoData.renderers, gotoData));
    }

    List<Object> allElements = new ArrayList<Object>(targets.length + additionalActions.size());
    Collections.addAll(allElements, targets);
    allElements.addAll(additionalActions);

    final JBListWithHintProvider list = new JBListWithHintProvider(new CollectionListModel<Object>(allElements)) {
      @Override
      protected PsiElement getPsiElementForHint(final Object selectedValue) {
        return selectedValue instanceof PsiElement ? (PsiElement) selectedValue : null;
      }
    };

    list.setFont(ChooseByNameBase.getEditorFont());
    
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof AdditionalAction) {
          return myActionElementRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
        PsiElementListCellRenderer renderer = getRenderer(value, gotoData.renderers, gotoData);
        return renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    final Runnable runnable = new Runnable() {
      @Override
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
            try {
              if (nav != null && nav.canNavigate()) {
                navigateToElement(nav);
              }
            }
            catch (IndexNotReadyException e) {
              DumbService.getInstance(project).showDumbModeNotification("Navigation is not available while indexing");
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
        return getRenderer(o, gotoData.renderers, gotoData).getElementText((PsiElement)o);
      }
    });

    final Ref<UsageView> usageView = new Ref<UsageView>();
    final JBPopup popup = builder.
      setTitle(title).
      setItemChoosenCallback(runnable).
      setMovable(true).
      setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          HintUpdateSupply.hideHint(list);
          return true;
        }
      }).
      setCouldPin(new Processor<JBPopup>() {
        @Override
        public boolean process(JBPopup popup) {
          usageView.set(FindUtil.showInUsageView(gotoData.source, gotoData.targets, getFindUsagesTitle(gotoData.source, name, gotoData.targets.length), project));
          popup.cancel();
          return false;
        }
      }).
      setAdText(getAdText(gotoData.source, targets.length)).
      createPopup();

    builder.getScrollPane().setBorder(null);
    builder.getScrollPane().setViewportBorder(null);

    if (gotoData.listUpdaterTask != null) {
      gotoData.listUpdaterTask.init((AbstractPopup)popup, list, usageView);
      ProgressManager.getInstance().run(gotoData.listUpdaterTask);
    }
    popup.showInBestPositionFor(editor);
  }

  private static PsiElementListCellRenderer getRenderer(Object value,
                                                        Map<Object, PsiElementListCellRenderer> targetsWithRenderers,
                                                        GotoData gotoData) {
    PsiElementListCellRenderer renderer = targetsWithRenderers.get(value);
    if (renderer == null) {
      renderer = gotoData.getRenderer(value);
    }
    if (renderer != null) {
      return renderer;
    }
    else {
      return ourDefaultTargetElementRenderer;
    }
  }

  protected static Comparator<PsiElement> createComparator(final Map<Object, PsiElementListCellRenderer> targetsWithRenderers,
                                                           final GotoData gotoData) {
    return new Comparator<PsiElement>() {
      @Override
      public int compare(PsiElement o1, PsiElement o2) {
        return getComparingObject(o1).compareTo(getComparingObject(o2));
      }

      private Comparable getComparingObject(PsiElement o1) {
        return getRenderer(o1, targetsWithRenderers, gotoData).getComparingObject(o1);
      }
    };
  }

  protected static PsiElementListCellRenderer createRenderer(GotoData gotoData, PsiElement eachTarget) {
    PsiElementListCellRenderer renderer = null;
    for (GotoTargetRendererProvider eachProvider : Extensions.getExtensions(GotoTargetRendererProvider.EP_NAME)) {
      renderer = eachProvider.getRenderer(eachTarget, gotoData);
      if (renderer != null) break;
    }
    if (renderer == null) {
      renderer = ourDefaultTargetElementRenderer;
    }
    return renderer;
  }


  protected void navigateToElement(Navigatable descriptor) {
    descriptor.navigate(true);
  }

  protected boolean shouldSortTargets() {
    return true;
  }

  @NotNull
  protected abstract String getChooserTitle(PsiElement sourceElement, String name, int length);
  @NotNull
  protected String getFindUsagesTitle(PsiElement sourceElement, String name, int length) {
    return getChooserTitle(sourceElement, name, length);
  }

  @NotNull
  protected abstract String getNotFoundMessage(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file);

  @Nullable
  protected String getAdText(PsiElement source, int length) {
    return null;
  }

  public interface AdditionalAction {
    @NotNull
    String getText();

    Icon getIcon();

    void execute();
  }

  public static class GotoData {
    @NotNull public final PsiElement source;
    public PsiElement[] targets;
    public final List<AdditionalAction> additionalActions;

    private boolean hasDifferentNames;
    public ListBackgroundUpdaterTask listUpdaterTask;
    protected final Set<String> myNames;
    public Map<Object, PsiElementListCellRenderer> renderers = new HashMap<Object, PsiElementListCellRenderer>();

    public GotoData(@NotNull PsiElement source, @NotNull PsiElement[] targets, @NotNull List<AdditionalAction> additionalActions) {
      this.source = source;
      this.targets = targets;
      this.additionalActions = additionalActions;

      myNames = new HashSet<String>();
      for (PsiElement target : targets) {
        if (target instanceof PsiNamedElement) {
          myNames.add(((PsiNamedElement)target).getName());
          if (myNames.size() > 1) break;
        }
      }

      hasDifferentNames = myNames.size() > 1;
    }

    public boolean hasDifferentNames() {
      return hasDifferentNames;
    }

    public boolean addTarget(final PsiElement element) {
      if (ArrayUtil.find(targets, element) > -1) return false;
      targets = ArrayUtil.append(targets, element);
      renderers.put(element, createRenderer(this, element));
      if (!hasDifferentNames && element instanceof PsiNamedElement) {
        final String name = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
          @Override
          public String compute() {
            return ((PsiNamedElement)element).getName();
          }
        });
        myNames.add(name);
        hasDifferentNames = myNames.size() > 1;
      }
      return true;
    }

    public PsiElementListCellRenderer getRenderer(Object value) {
      return renderers.get(value);
    }
  }

  private static class DefaultPsiElementListCellRenderer extends PsiElementListCellRenderer {
    @Override
    public String getElementText(final PsiElement element) {
      if (element instanceof PsiNamedElement) {
        String name = ((PsiNamedElement)element).getName();
        if (name != null) {
          return name;
        }
      }
      return element.getContainingFile().getName();
    }

    @Override
    protected String getContainerText(final PsiElement element, final String name) {
      if (element instanceof NavigationItem) {
        final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        return presentation != null ? presentation.getLocationString():null;
      }

      return null;
    }

    @Override
    protected int getIconFlags() {
      return 0;
    }
  }

  private static class ActionCellRenderer extends DefaultListCellRenderer {
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
