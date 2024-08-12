// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.hierarchy.*;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;


public abstract class BrowseHierarchyActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance(BrowseHierarchyActionBase.class);
  private final LanguageExtension<HierarchyProvider> myExtension;

  protected BrowseHierarchyActionBase(@NotNull LanguageExtension<HierarchyProvider> extension) {
    myExtension = extension;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    HierarchyProvider provider = getProvider(e);
    if (provider == null) return;
    PsiElement target = provider.getTarget(dataContext);
    if (target == null) return;
    createAndAddToPanel(project, provider, target);
  }

  public static @NotNull HierarchyBrowser createAndAddToPanel(@NotNull Project project,
                                                              @NotNull HierarchyProvider provider,
                                                              @NotNull PsiElement target) {
    HierarchyBrowser hierarchyBrowser = provider.createHierarchyBrowser(target);

    HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(project);

    ContentManager contentManager = hierarchyBrowserManager.getContentManager();
    Content selectedContent = contentManager.getSelectedContent();

    JComponent browserComponent = hierarchyBrowser.getComponent();

    if (selectedContent != null && !selectedContent.isPinned()) {
      contentManager.removeContent(selectedContent, true);
    }
    Content content = ContentFactory.getInstance().createContent(browserComponent, null, true);
    if (!DumbService.isDumbAware(hierarchyBrowser)) {
      browserComponent = DumbService.getInstance(project).wrapGently(browserComponent, content);
      content.setComponent(browserComponent);
    }
    contentManager.addContent(content);

    content.setHelpId(HierarchyBrowserBaseEx.HELP_ID);
    contentManager.setSelectedContent(content);
    hierarchyBrowser.setContent(content);

    Runnable runnable = () -> {
      if (hierarchyBrowser instanceof HierarchyBrowserBase && ((HierarchyBrowserBase)hierarchyBrowser).isDisposed()) {
        return;
      }
      provider.browserActivated(hierarchyBrowser);
    };
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY);
    toolWindow.activate(runnable);
    if (hierarchyBrowser instanceof Disposable) {
      Disposer.register(content, (Disposable)hierarchyBrowser);
    }
    return hierarchyBrowser;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!myExtension.hasAnyExtensions()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      boolean enabled = isEnabled(e);
      if (ActionPlaces.isPopupPlace(e.getPlace())) {
        e.getPresentation().setVisible(enabled);
      }
      else {
        e.getPresentation().setVisible(true);
      }
      e.getPresentation().setEnabled(enabled);
    }
  }

  private boolean isEnabled(AnActionEvent e) {
    HierarchyProvider provider = getProvider(e);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Using provider " + provider);
    }
    if (provider == null) return false;
    PsiElement target = provider.getTarget(e.getDataContext());
    if (LOG.isDebugEnabled()) {
      LOG.debug("Target: " + target);
    }
    return target != null;
  }

  private @Nullable HierarchyProvider getProvider(@NotNull AnActionEvent e) {
    return findProvider(myExtension, e.getData(CommonDataKeys.PSI_ELEMENT), e.getData(CommonDataKeys.PSI_FILE), e.getDataContext());
  }

  public static @Nullable HierarchyProvider findProvider(@NotNull LanguageExtension<HierarchyProvider> extension,
                                                         @Nullable PsiElement psiElement,
                                                         @Nullable PsiFile psiFile,
                                                         @NotNull DataContext dataContext) {
    HierarchyProvider provider = findBestHierarchyProvider(extension, psiElement, dataContext);
    if (provider == null) {
      return findBestHierarchyProvider(extension, psiFile, dataContext);
    }
    return provider;
  }

  public static @Nullable HierarchyProvider findBestHierarchyProvider(LanguageExtension<HierarchyProvider> extension,
                                                                      @Nullable PsiElement element,
                                                                      DataContext dataContext) {
    if (element == null) return null;
    List<HierarchyProvider> providers = extension.allForLanguage(element.getLanguage());
    for (HierarchyProvider provider : providers) {
      PsiElement target = provider.getTarget(dataContext);
      if (target != null) {
        return provider;
      }
    }
    return ContainerUtil.getFirstItem(providers);
  }
}
