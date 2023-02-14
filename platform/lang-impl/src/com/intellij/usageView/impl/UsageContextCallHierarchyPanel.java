// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usageView.impl;

import com.intellij.ide.hierarchy.*;
import com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;
import com.intellij.usages.impl.UsageContextPanelBase;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UsageContextCallHierarchyPanel extends UsageContextPanelBase {
  private HierarchyBrowser myBrowser;

  public static class Provider implements UsageContextPanel.Provider {
    @NotNull
    @Override
    public UsageContextPanel create(@NotNull UsageView usageView) {
      return new UsageContextCallHierarchyPanel(((UsageViewImpl)usageView).getProject(), usageView.getPresentation());
    }

    @Override
    public boolean isAvailableFor(@NotNull UsageView usageView) {
      UsageTarget[] targets = ((UsageViewImpl)usageView).getTargets();
      if (targets.length == 0) return false;
      UsageTarget target = targets[0];
      if (!(target instanceof PsiElementUsageTarget)) return false;
      PsiElement element = ((PsiElementUsageTarget)target).getElement();
      if (element == null || !element.isValid()) return false;

      Project project = element.getProject();
      DataContext context = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.PSI_ELEMENT, element)
        .build();
      HierarchyProvider provider = BrowseHierarchyActionBase.findBestHierarchyProvider(LanguageCallHierarchy.INSTANCE, element, context);
      if (provider == null) return false;
      PsiElement providerTarget = provider.getTarget(context);
      return providerTarget != null;
    }
    @NotNull
    @Override
    public String getTabTitle() {
      return LangBundle.message("tab.title.call.hierarchy");
    }
  }

  public UsageContextCallHierarchyPanel(@NotNull Project project, @NotNull UsageViewPresentation presentation) {
    super(project, presentation);
  }

  @Override
  public void dispose() {
    super.dispose();
    myBrowser = null;
  }

  @Override
  public void updateLayoutLater(@Nullable final List<? extends UsageInfo> infos) {
    PsiElement element = ContainerUtil.isEmpty(infos) ? null : getElementToSliceOn(infos);
    if (myBrowser instanceof Disposable) {
      Disposer.dispose((Disposable)myBrowser);
      myBrowser = null;
    }
    if (element != null) {
      myBrowser = createCallHierarchyPanel(element);
      if (myBrowser == null) {
        element = null;
      }
    }

    removeAll();
    if (element == null) {
      JComponent titleComp = new JLabel(UsageViewBundle.message("select.the.usage.to.preview"), SwingConstants.CENTER);
      add(titleComp, BorderLayout.CENTER);
    }
    else {
      if (myBrowser instanceof Disposable) {
        Disposer.register(this, (Disposable)myBrowser);
      }
      JComponent panel = myBrowser.getComponent();
      add(panel, BorderLayout.CENTER);
    }
    revalidate();
  }

  @Nullable
  private static HierarchyBrowser createCallHierarchyPanel(@NotNull PsiElement element) {
    DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, element.getProject())
      .add(CommonDataKeys.PSI_ELEMENT, element)
      .build();
    HierarchyProvider provider = BrowseHierarchyActionBase.findBestHierarchyProvider(LanguageCallHierarchy.INSTANCE, element, context);
    if (provider == null) return null;
    PsiElement providerTarget = provider.getTarget(context);
    if (providerTarget == null) return null;

    HierarchyBrowser browser = provider.createHierarchyBrowser(providerTarget);
    if (browser instanceof HierarchyBrowserBaseEx browserEx) {
      // do not steal focus when scrolling through nodes
      browserEx.changeView(CallHierarchyBrowserBase.getCallerType(), false);
    }
    return browser;
  }

  private static PsiElement getElementToSliceOn(@NotNull List<? extends UsageInfo> infos) {
    UsageInfo info = infos.get(0);
    return info.getElement();
  }
}
