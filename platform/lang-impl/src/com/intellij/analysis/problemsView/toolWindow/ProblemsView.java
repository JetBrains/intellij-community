// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.ToolWindowEventSource;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.ide.actions.ToggleToolbarAction.isToolbarVisible;
import static com.intellij.psi.util.PsiUtilCore.findFileSystemItem;

public final class ProblemsView implements DumbAware, ToolWindowFactory {
  public static final String ID = "Problems View";

  public static @Nullable ToolWindow getToolWindow(@Nullable Project project) {
    return project == null || project.isDisposed() ? null : ToolWindowManager.getInstance(project).getToolWindow(ID);
  }

  public static void toggleCurrentFileProblems(@NotNull Project project, @Nullable VirtualFile file) {
    ToolWindow window = getToolWindow(project);
    if (window == null) return; // does not exist
    ContentManager manager = window.getContentManager();
    HighlightingPanel panel = get(HighlightingPanel.class, manager.getSelectedContent());
    ToolWindowManagerImpl toolWindowManager = (ToolWindowManagerImpl) ToolWindowManager.getInstance(project);
    if (file == null || panel == null || !panel.isShowing()) {
      ProblemsViewToolWindowUtils.INSTANCE.selectContent(manager, HighlightingPanel.ID);
      window.setAvailable(true, null);
      toolWindowManager.activateToolWindow(window.getId(), null, true, ToolWindowEventSource.InspectionsWidget);
    }
    else if (file.equals(panel.getCurrentFile())) {
      toolWindowManager.hideToolWindow(window.getId(), false, true, false, ToolWindowEventSource.InspectionsWidget);
    }
    else {
      panel.setCurrentFile(file);
      toolWindowManager.activateToolWindow(window.getId(), null, true, ToolWindowEventSource.InspectionsWidget);
    }
  }

  public static void selectHighlighterIfVisible(@NotNull Project project, @NotNull RangeHighlighterEx highlighter) {
    HighlightingPanel panel = get(HighlightingPanel.class, getSelectedContent(project));
    if (panel != null && panel.isShowing()) panel.selectHighlighter(highlighter);
  }

  public static @Nullable Document getDocument(@Nullable Project project, @NotNull VirtualFile file) {
    Object item = file.isDirectory() ? null : findFileSystemItem(project, file);
    return item instanceof PsiFile ? PsiDocumentManager.getInstance(project).getDocument((PsiFile)item) : null;
  }

  public static @Nullable ProblemsViewPanel getSelectedPanel(@Nullable Project project) {
    return get(ProblemsViewPanel.class, getSelectedContent(project));
  }

  private static @Nullable Content getSelectedContent(@Nullable Project project) {
    ToolWindow window = getToolWindow(project);
    ContentManager manager = window == null ? null : window.getContentManagerIfCreated();
    return manager == null ? null : manager.getSelectedContent();
  }

  private static void createContent(@NotNull ContentManager manager, @NotNull ProblemsViewTab panel) {
    if (!(panel instanceof JComponent))
      throw new IllegalArgumentException("panel is not JComponent");

    var component = (JComponent)panel;
    Content content = manager.getFactory().createContent(component, panel.getName(0), false);
    content.setCloseable(false);
    manager.addContent(content);
  }

  private static void selectionChanged(boolean selected, @Nullable Content content) {
    ProblemsViewPanel panel = get(ProblemsViewPanel.class, content);
    if (panel != null) panel.selectionChangedTo(selected);
  }

  private static void visibilityChanged(boolean visible, @Nullable Content content) {
    ProblemsViewPanel panel = get(ProblemsViewPanel.class, content);
    if (panel != null) panel.visibilityChangedTo(visible);
  }

  @SuppressWarnings("unchecked")
  private static <T> @Nullable T get(@NotNull Class<T> type, @Nullable Content content) {
    JComponent component = content == null ? null : content.getComponent();
    return type.isInstance(component) ? (T)component : null;
  }

  @Override
  public void init(@NotNull ToolWindow window) {
    Project project = ((ToolWindowEx)window).getProject();
    HighlightingErrorsProviderBase.getInstance(project);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
    ProblemsViewState state = ProblemsViewState.getInstance(project);
    state.setShowToolbar(isToolbarVisible(window, project));
    ContentManager manager = window.getContentManager();

    for (ProblemsViewPanelProvider provider : ProblemsViewPanelProvider.getEP().getExtensions(project)) {
      var panel = provider.create();
      if (panel == null) continue;
      createContent(manager, panel);
    }

    var selectedTabId = state.getSelectedTabId();
    if (selectedTabId != null) {
      ProblemsViewToolWindowUtils.INSTANCE.selectContent(manager, selectedTabId);
    }

    selectionChanged(true, manager.getSelectedContent());
    manager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        boolean selected = ContentManagerEvent.ContentOperation.add == event.getOperation();
        var component = event.getContent().getComponent();
        if (component instanceof ProblemsViewTab) {
          var problemsView = (ProblemsViewTab)component;
          ProblemsView.selectionChanged(selected, event.getContent());
          if (selected)
            state.setSelectedTabId(problemsView.getTabId());
        }
      }
    });
    project.getMessageBus().connect(manager).subscribe(ToolWindowManagerListener.TOPIC, createListener());
  }

  @NotNull
  private static ToolWindowManagerListener createListener() {
    return new ToolWindowManagerListener() {
      private final AtomicBoolean orientation = new AtomicBoolean();
      private final AtomicBoolean visibility = new AtomicBoolean(true);

      @Override
      public void stateChanged(@NotNull ToolWindowManager manager) {
        ToolWindow window = manager.getToolWindow(ID);
        if (window == null || window.isDisposed()) return;

        boolean vertical = !window.getAnchor().isHorizontal();
        if (vertical != orientation.getAndSet(vertical)) {
          for (Content content : window.getContentManager().getContents()) {
            ProblemsViewPanel panel = get(ProblemsViewPanel.class, content);
            if (panel != null) panel.orientationChangedTo(vertical);
          }
        }
        boolean visible = window.isVisible();
        if (visible != visibility.getAndSet(visible)) {
          visibilityChanged(visible, window.getContentManager().getSelectedContent());
        }
      }
    };
  }
}
