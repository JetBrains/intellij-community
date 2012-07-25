/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeTooltip;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;

/**
 * @author Denis Zhdanov
 * @since 7/13/12 11:43 AM
 */
public class ShowQuickDocAtPinnedWindowFromTooltipAction extends AnAction {

  @NotNull private final IdeTooltipManager myTooltipManager = IdeTooltipManager.getInstance();
  @NotNull private final DataManager       myDataManager    = DataManager.getInstance();

  @Nullable private WeakReference<Pair<PsiElement, PsiElement>> myInfo;

  public ShowQuickDocAtPinnedWindowFromTooltipAction() {
    String className = getClass().getSimpleName();
    String actionId = className.substring(0, className.lastIndexOf("Action"));
    getTemplatePresentation().setText(ActionsBundle.actionText(actionId));
    getTemplatePresentation().setDescription(ActionsBundle.actionDescription(actionId));
    getTemplatePresentation().setIcon(AllIcons.General.Pin_tab);
  }

  @Override
  public void update(AnActionEvent e) {

    // We can't use data context from the given event because it's built from the focused component and IDE tooltip doesn't have focus.
    IdeTooltip tooltip = myTooltipManager.getCurrentTooltip();
    if (tooltip == null) {
      return;
    }

    JComponent component = tooltip.getTipComponent();
    if (component == null) {
      return;
    }

    Pair<PsiElement, PsiElement> info = CtrlMouseHandler.ELEMENT_UNDER_MOUSE_INFO_KEY.getData(myDataManager.getDataContext(component));
    if (info != null) {
      // Target info is retrieved during AnAction.update() processing because IDE tooltip is closed on action activation,
      // i.e. IdeTooltipManager.getCurrentComponent() returns null during AnAction.actionPerformed() execution.
      myInfo = new WeakReference<Pair<PsiElement, PsiElement>>(info);
    }
  }
  
  @Override
  public void actionPerformed(AnActionEvent e) {
    WeakReference<Pair<PsiElement, PsiElement>> infoRef = myInfo;
    if (infoRef == null) {
      return;
    }
    Pair<PsiElement, PsiElement> info = infoRef.get();
    if (info == null) {
      return;
    }

    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    myInfo = null;
    DocumentationManager docManager = DocumentationManager.getInstance(project);
    docManager.showJavaDocInfoAtToolWindow(info.first, info.second);
  }
}
