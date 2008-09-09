package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyBrowserManager;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
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
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public abstract class BrowseHierarchyActionBase extends AnAction {
  private LanguageExtension<HierarchyProvider> myExtension;

  protected BrowseHierarchyActionBase(final LanguageExtension<HierarchyProvider> extension) {
    myExtension = extension;
  }

  public final void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments(); // prevents problems with smart pointers creation

    final HierarchyProvider provider = getProvider(e);
    if (provider == null) return;
    final PsiElement target = provider.getTarget(dataContext);
    if (target == null) return;
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
      public void run() {
        provider.browserActivated(hierarchyBrowser);
      }
    };
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY).activate(runnable);
  }

  @Override
  public void update(final AnActionEvent e) {
    if (!myExtension.hasAnyExtensions()) {
      e.getPresentation().setVisible(false);
    }
    else {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(isEnabled(e));
    }
  }

  private boolean isEnabled(final AnActionEvent e) {
    final HierarchyProvider provider = getProvider(e);
    return provider != null && provider.getTarget(e.getDataContext()) != null;
  }

  @Nullable
  private HierarchyProvider getProvider(final AnActionEvent e) {
    PsiFile file = e.getData(LangDataKeys.PSI_FILE);
    if (file == null) return null;

    return myExtension.forLanguage(file.getLanguage());
  }
}
