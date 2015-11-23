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

package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
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

import java.awt.*;
import java.util.List;

/**
 * @author yole
 */
public abstract class BrowseHierarchyActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.hierarchy.actions.BrowseHierarchyActionBase");
  private final LanguageExtension<HierarchyProvider> myExtension;

  protected BrowseHierarchyActionBase(@NotNull LanguageExtension<HierarchyProvider> extension) {
    myExtension = extension;
  }

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = e.getProject();
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    final HierarchyProvider provider = getProvider(e);
    if (provider == null) return;
    final PsiElement target = provider.getTarget(dataContext);
    if (target == null) return;
    createAndAddToPanel(project, provider, target);
  }

  public static HierarchyBrowser createAndAddToPanel(@NotNull Project project, @NotNull final HierarchyProvider provider, @NotNull PsiElement target) {
    final HierarchyBrowser hierarchyBrowser = provider.createHierarchyBrowser(target);

    final Content content;

    final HierarchyBrowserManager hierarchyBrowserManager = HierarchyBrowserManager.getInstance(project);

    final ContentManager contentManager = hierarchyBrowserManager.getContentManager();
    final Content selectedContent = contentManager.getSelectedContent();
    if (selectedContent != null && !selectedContent.isPinned()) {
      content = selectedContent;
      final Component component = content.getComponent();
      if (component instanceof Disposable) {
        Disposer.dispose((Disposable)component);
      }
      content.setComponent(hierarchyBrowser.getComponent());
    }
    else {
      content = ContentFactory.SERVICE.getInstance().createContent(hierarchyBrowser.getComponent(), null, true);
      contentManager.addContent(content);
    }
    contentManager.setSelectedContent(content);
    hierarchyBrowser.setContent(content);

    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        provider.browserActivated(hierarchyBrowser);
      }
    };
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY).activate(runnable);
    return hierarchyBrowser;
  }

  @Override
  public void update(final AnActionEvent e) {
    if (!myExtension.hasAnyExtensions()) {
      e.getPresentation().setVisible(false);
    }
    else {
      final boolean enabled = isEnabled(e);
      if (ActionPlaces.isPopupPlace(e.getPlace())) {
        e.getPresentation().setVisible(enabled);
      }
      else {
        e.getPresentation().setVisible(true);
      }
      e.getPresentation().setEnabled(enabled);
    }
  }

  private boolean isEnabled(final AnActionEvent e) {
    final HierarchyProvider provider = getProvider(e);
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

  @Nullable
  private HierarchyProvider getProvider(final AnActionEvent e) {
    return findProvider(myExtension, e.getData(CommonDataKeys.PSI_ELEMENT), e.getData(CommonDataKeys.PSI_FILE), e.getDataContext());
  }

  @Nullable
  public static HierarchyProvider findProvider(@NotNull LanguageExtension<HierarchyProvider> extension,
                                               @Nullable PsiElement psiElement,
                                               @Nullable PsiFile psiFile,
                                               @NotNull DataContext dataContext) {
    final HierarchyProvider provider = findBestHierarchyProvider(extension, psiElement, dataContext);
    if (provider == null) {
      return findBestHierarchyProvider(extension, psiFile, dataContext);
    }
    return provider;
  }

  @Nullable
  public static HierarchyProvider findBestHierarchyProvider(final LanguageExtension<HierarchyProvider> extension,
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
