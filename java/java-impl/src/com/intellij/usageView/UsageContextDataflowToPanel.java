// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView;

import com.intellij.analysis.AnalysisScope;
import com.intellij.java.JavaBundle;
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
import com.intellij.slicer.DuplicateMap;
import com.intellij.slicer.JavaSliceUsage;
import com.intellij.slicer.SliceAnalysisParams;
import com.intellij.slicer.SlicePanel;
import com.intellij.slicer.SliceRootNode;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageContextPanel;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageContextPanelBase;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.util.List;

public class UsageContextDataflowToPanel extends UsageContextPanelBase {
  private JComponent myPanel;

  public static class Provider implements UsageContextPanel.Provider {
    @Override
    public @NotNull UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsageContextDataflowToPanel(usageView.getPresentation());
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
    @Override
    public @NotNull String getTabTitle() {
      return JavaBundle.message("dataflow.to.here");
    }
  }

  public UsageContextDataflowToPanel(@NotNull UsageViewPresentation presentation) {
    super(presentation);
  }

  @Override
  public void dispose() {
    super.dispose();
    myPanel = null;
  }

  @Override
  public void updateLayoutLater(final @Nullable List<? extends UsageInfo> infos) {
    if (ContainerUtil.isEmpty(infos)) {
      removeAll();
      JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview"), SwingConstants.CENTER);
      add(titleComp, BorderLayout.CENTER);
    }
    else {
      PsiElement element = getElementToSliceOn(infos);
      if (element == null) return;
      if (myPanel != null) {
        Disposer.dispose((Disposable)myPanel);
      }

      PsiElement restored = JavaSliceUsage.createRootUsage(element, createParams(element, isDataflowToThis())).getElement();
      if (restored == null || restored.getContainingFile() == null) return;

      JComponent panel = createPanel(element, isDataflowToThis());
      myPanel = panel;
      Disposer.register(this, (Disposable)panel);
      removeAll();
      add(panel, BorderLayout.CENTER);
    }
    revalidate();
  }

  protected boolean isDataflowToThis() {
    return true;
  }

  private static @NotNull SliceAnalysisParams createParams(PsiElement element, boolean dataFlowToThis) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.scope = new AnalysisScope(element.getProject());
    params.dataFlowToThis = dataFlowToThis;
    params.showInstanceDereferences = true;
    return params;
  }

  protected @NotNull JComponent createPanel(@NotNull PsiElement element, final boolean dataFlowToThis) {
    Project project = element.getProject();
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.FIND);
    SliceAnalysisParams params = createParams(element, dataFlowToThis);

    SliceRootNode rootNode = new SliceRootNode(project, new DuplicateMap(), JavaSliceUsage.createRootUsage(element, params));

    return new SlicePanel(project, dataFlowToThis, rootNode, false, toolWindow) {
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

  private static PsiElement getElementToSliceOn(@NotNull List<? extends UsageInfo> infos) {
    UsageInfo info = infos.get(0);
    return info.getElement();
  }
}
