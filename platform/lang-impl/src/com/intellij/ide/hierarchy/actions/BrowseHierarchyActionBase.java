// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyBrowserBase;
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.platform.ide.productMode.IdeProductMode;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
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
  public boolean isDumbAware() {
    return true;
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    HierarchyProvider provider = getProvider(e);
    if (provider == null) {
      if (DumbService.isDumb(project)) {
        ActionUtil.showDumbModeWarning(project, this, e);
      }
      return;
    }
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
      Project project = e.getProject();
      boolean enabled = isEnabled(e) ||
                        project != null && DumbService.isDumb(project) && !IdeProductMode.isLight() && !e.isFromContextMenu() &&
                        hasAnyProvider(e);
      if (e.isFromContextMenu() || IdeProductMode.isLight()) {
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
    PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
    return findProvider(myExtension, psiElement, e.getData(CommonDataKeys.PSI_FILE), e.getDataContext(), true);
  }

  /** Whether a provider serves the element or the file of {@code e}, without asking it for its target: the target can need the indexes. */
  private boolean hasAnyProvider(@NotNull AnActionEvent e) {
    return !providersFor(myExtension, e.getData(CommonDataKeys.PSI_ELEMENT)).isEmpty() ||
           !providersFor(myExtension, e.getData(CommonDataKeys.PSI_FILE)).isEmpty();
  }

  /**
   * Considers every provider, also in the dumb mode: a caller that runs in the dumb mode handles it itself,
   * as the MCP call hierarchy reports a partial result. The hierarchy actions use only the dumb-aware providers there.
   */
  public static @Nullable HierarchyProvider findProvider(@NotNull LanguageExtension<HierarchyProvider> extension,
                                                         @Nullable PsiElement psiElement,
                                                         @Nullable PsiFile psiFile,
                                                         @NotNull DataContext dataContext) {
    return findProvider(extension, psiElement, psiFile, dataContext, false);
  }

  private static @Nullable HierarchyProvider findProvider(@NotNull LanguageExtension<HierarchyProvider> extension,
                                                          @Nullable PsiElement psiElement,
                                                          @Nullable PsiFile psiFile,
                                                          @NotNull DataContext dataContext,
                                                          boolean dumbAwareOnly) {
    HierarchyProvider provider = findBestHierarchyProvider(extension, psiElement, dataContext, dumbAwareOnly);
    if (provider == null) {
      return findBestHierarchyProvider(extension, psiFile, dataContext, dumbAwareOnly);
    }
    return provider;
  }

  /** Considers every provider, also in the dumb mode, as {@link #findProvider} does. */
  public static @Nullable HierarchyProvider findBestHierarchyProvider(LanguageExtension<HierarchyProvider> extension,
                                                                      @Nullable PsiElement element,
                                                                      DataContext dataContext) {
    return findBestHierarchyProvider(extension, element, dataContext, false);
  }

  /**
   * The first provider that finds its target, else the first one.
   *
   * @param dumbAwareOnly whether to skip the providers that are not dumb-aware in the dumb mode.
   */
  private static @Nullable HierarchyProvider findBestHierarchyProvider(LanguageExtension<HierarchyProvider> extension,
                                                                       @Nullable PsiElement element,
                                                                       DataContext dataContext,
                                                                       boolean dumbAwareOnly) {
    if (element == null) return null;
    List<HierarchyProvider> providers = providersFor(extension, element);
    if (dumbAwareOnly) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      DumbService dumbService = DumbService.getInstance(project != null ? project : element.getProject());
      providers = ContainerUtil.filter(providers, dumbService::isUsableInCurrentContext);
    }
    for (HierarchyProvider provider : providers) {
      PsiElement target = provider.getTarget(dataContext);
      if (target != null) {
        return provider;
      }
    }
    return ContainerUtil.getFirstItem(providers);
  }

  private static @NotNull List<HierarchyProvider> providersFor(@NotNull LanguageExtension<HierarchyProvider> extension,
                                                               @Nullable PsiElement element) {
    if (element == null) return List.of();
    return element instanceof PsiFile
           ? extension.allForLanguageOrAny(element.getLanguage())
           : extension.allForLanguage(element.getLanguage());
  }
}
