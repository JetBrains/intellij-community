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

package com.intellij.ide.hierarchy;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class MethodHierarchyBrowserBase extends HierarchyBrowserBaseEx {

  @SuppressWarnings({"UnresolvedPropertyKey"})
  public static final String METHOD_TYPE = IdeBundle.message("title.hierarchy.method");

  public static final DataKey<MethodHierarchyBrowserBase> DATA_KEY = DataKey.create("com.intellij.ide.hierarchy.MethodHierarchyBrowserBase");
  @Deprecated @NonNls public static final String METHOD_HIERARCHY_BROWSER_DATA_KEY = DATA_KEY.getName();

  public MethodHierarchyBrowserBase(final Project project, final PsiElement method) {
    super(project, method);
  }

  @NotNull
  protected String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.method.prev.occurence.name");
  }

  @NotNull
  protected String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.method.next.occurence.name");
  }

  protected static JPanel createStandardLegendPanel(final String methodDefinedText,
                                                    final String methodNotDefinedLegallyText,
                                                    final String methodShouldBeDefined) {
    final JPanel panel = new JPanel(new GridBagLayout());

    JLabel label;
    final GridBagConstraints gc =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(3, 5, 0, 5), 0, 0);

    label = new JLabel(methodDefinedText, IconLoader.getIcon("/hierarchy/methodDefined.png"), SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    gc.gridy++;
    label = new JLabel(methodNotDefinedLegallyText, IconLoader.getIcon("/hierarchy/methodNotDefined.png"), SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    gc.gridy++;
    label = new JLabel(methodShouldBeDefined, IconLoader.getIcon("/hierarchy/shouldDefineMethod.png"), SwingConstants.LEFT);
    label.setUI(new MultiLineLabelUI());
    label.setIconTextGap(10);
    panel.add(label, gc);

    return panel;
  }

  @Override
  protected void prependActions(@NotNull DefaultActionGroup actionGroup) {
    actionGroup.add(new AlphaSortAction());
    actionGroup.add(new ShowImplementationsOnlyAction());
  }

  @NotNull
  protected String getBrowserDataKey() {
    return DATA_KEY.getName();
  }

  @NotNull
  protected String getActionPlace() {
    return ActionPlaces.METHOD_HIERARCHY_VIEW_TOOLBAR;
  }

  final class ShowImplementationsOnlyAction extends ToggleAction {
    public ShowImplementationsOnlyAction() {
      super(IdeBundle.message("action.hide.non.implementations"), null,
            IconLoader.getIcon("/ant/filter.png")); // TODO[anton] use own icon!!!
    }

    public final boolean isSelected(final AnActionEvent event) {
      return HierarchyBrowserManager.getInstance(myProject).getState().HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED;
    }

    public final void setSelected(final AnActionEvent event, final boolean flag) {
      HierarchyBrowserManager.getInstance(myProject).getState().HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED = flag;

      // invokeLater is called to update state of button before long tree building operation
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          doRefresh(true);
        }
      });
    }

    public final void update(final AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(isValidBase());
    }
  }

  public static class BaseOnThisMethodAction extends BaseOnThisElementAction {
    public BaseOnThisMethodAction() {
      super(IdeBundle.message("action.base.on.this.method"), IdeActions.ACTION_METHOD_HIERARCHY, DATA_KEY.getName());
    }
  }

}
