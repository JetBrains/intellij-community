/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.usageView;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiVariable;
import com.intellij.slicer.*;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageContextPanelBase;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UsageContextDataflowToPanel extends UsageContextPanelBase {
  @NotNull private final UsageViewPresentation myPresentation;
  private JComponent myPanel;

  public static class Provider implements UsageContextPanel.Provider {
    @NotNull
    @Override
    public UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsageContextDataflowToPanel(((UsageViewImpl)usageView).getProject(), usageView.getPresentation());
    }

    @Override
    public boolean isAvailableFor(@NotNull UsageView usageView) {
      UsageTarget[] targets = ((UsageViewImpl)usageView).getTargets();
      if (targets.length == 0) return false;
      UsageTarget target = targets[0];
      if (!(target instanceof PsiElementUsageTarget)) return false;
      PsiElement element = ((PsiElementUsageTarget)target).getElement();
      if (element == null || !element.isValid()) return false;
      if (!(element instanceof PsiVariable)) return false;
      PsiFile file = element.getContainingFile();
      return file instanceof PsiJavaFile;
    }
    @NotNull
    @Override
    public String getTabTitle() {
      return "Dataflow to Here";
    }
  }

  public UsageContextDataflowToPanel(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    super(project, presentation);
    myPresentation = presentation;
  }

  @Override
  public void dispose() {
    super.dispose();
    myPanel = null;
  }

  @Override
  public void updateLayoutLater(@Nullable final List<UsageInfo> infos) {
    if (infos == null) {
      removeAll();
      JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview", myPresentation.getUsagesWord()), SwingConstants.CENTER);
      add(titleComp, BorderLayout.CENTER);
      revalidate();
    }
    else {
      PsiElement element = getElementToSliceOn(infos);
      if (element == null) return;
      if (myPanel != null) {
        Disposer.dispose((Disposable)myPanel);
      }

      JComponent panel = createPanel(element, isDataflowToThis());
      myPanel = panel;
      Disposer.register(this, (Disposable)panel);
      removeAll();
      add(panel, BorderLayout.CENTER);
      revalidate();
    }
  }

  protected boolean isDataflowToThis() {
    return true;
  }

  @NotNull
  private static SliceAnalysisParams createParams(PsiElement element, boolean dataFlowToThis) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(element.getProject());
    params.dataFlowToThis = dataFlowToThis;
    params.showInstanceDereferences = true;
    return params;
  }

  @NotNull
  protected JComponent createPanel(@NotNull PsiElement element, final boolean dataFlowToThis) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    SliceAnalysisParams params = createParams(element, dataFlowToThis);

    SliceRootNode rootNode = new SliceRootNode(myProject, new DuplicateMap(), JavaSliceUsage.createRootUsage(element, params));

    return new SlicePanel(myProject, dataFlowToThis, rootNode, false, toolWindow) {
      @Override
      public boolean isToShowAutoScrollButton() {
        return false;
      }

      @Override
      public boolean isToShowPreviewButton() {
        return false;
      }

      @Override
      public boolean isAutoScroll() {
        return false;
      }

      @Override
      public void setAutoScroll(boolean autoScroll) {
      }

      @Override
      public boolean isPreview() {
        return false;
      }

      @Override
      public void setPreview(boolean preview) {
      }
    };
  }

  private static PsiElement getElementToSliceOn(@NotNull List<UsageInfo> infos) {
    UsageInfo info = infos.get(0);
    return info.getElement();
  }
}
