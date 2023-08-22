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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.PlaceInProjectStructure;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElementUsage;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListCellRendererWithRightAlignedComponent;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

public abstract class FindUsagesInProjectStructureActionBase extends AnAction implements DumbAware {
  private final JComponent myParentComponent;
  private final ProjectStructureConfigurable myProjectStructureConfigurable;

  public FindUsagesInProjectStructureActionBase(JComponent parentComponent, ProjectStructureConfigurable projectStructureConfigurable) {
    super(ProjectBundle.message("find.usages.action.text"), ProjectBundle.message("find.usages.action.text"), AllIcons.Actions.Find);
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES).getShortcutSet(), parentComponent);
    myParentComponent = parentComponent;
    myProjectStructureConfigurable = projectStructureConfigurable;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  protected abstract boolean isEnabled();

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ProjectStructureElement selected = getSelectedElement();
    if (selected == null) return;

    final Collection<ProjectStructureElementUsage> usages = getContext().getDaemonAnalyzer().getUsages(selected);
    if (usages.isEmpty()) {
      Messages.showInfoMessage(myParentComponent, JavaUiBundle.message("find.usage.view.no.usages.text"), JavaUiBundle.message("find.pointcut.applications.not.found.title"));
      return;
    }

    RelativePoint point = getPointToShowResults();
    final ProjectStructureElementUsage[] usagesArray = usages.toArray(new ProjectStructureElementUsage[0]);
    Arrays.sort(usagesArray, (o1, o2) -> o1.getPresentableName().compareToIgnoreCase(o2.getPresentableName()));

    BaseListPopupStep<ProjectStructureElementUsage> step =
      new BaseListPopupStep<>(JavaUiBundle.message("dependencies.used.in.popup.title"), usagesArray) {
        @Override
        public PopupStep onChosen(final ProjectStructureElementUsage selected, final boolean finalChoice) {
          PlaceInProjectStructure place = selected.getPlace();
          if (place.canNavigate()) {
            place.navigate();
          }
          return FINAL_CHOICE;
        }

        @NotNull
        @Override
        public String getTextFor(ProjectStructureElementUsage value) {
          return value.getPresentableName();
        }

        @Override
        public Icon getIconFor(ProjectStructureElementUsage selection) {
          return selection.getIcon();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }
      };
    new ListPopupImpl(myProjectStructureConfigurable.getProject(), step) {
      @Override
      protected ListCellRenderer getListElementRenderer() {
        return new ListCellRendererWithRightAlignedComponent<ProjectStructureElementUsage>() {
          @Override
          protected void customize(ProjectStructureElementUsage value) {
            setLeftText(value.getPresentableName());
            setIcon(value.getIcon());
            setLeftForeground(value.getPlace().canNavigate() ? UIUtil.getLabelTextForeground() : UIUtil.getLabelDisabledForeground());
            setRightForeground(JBColor.GRAY);
            setRightText(value.getPresentableLocationInElement());
          }
        };
      }
    }.show(point);
  }

  @Nullable
  protected abstract ProjectStructureElement getSelectedElement();

  protected StructureConfigurableContext getContext() {
    return myProjectStructureConfigurable.getContext();
  }

  protected abstract RelativePoint getPointToShowResults();
}
