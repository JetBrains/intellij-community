// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
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
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.ide.actions.ToggleToolbarAction.isToolbarVisible;
import static com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText;
import static com.intellij.openapi.util.registry.Registry.is;
import static com.intellij.psi.util.PsiUtilCore.findFileSystemItem;
import static com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES;

public final class ProblemsView implements DumbAware, ToolWindowFactory {
  public static final String ID = "Problems View";
  private static final int CURRENT_FILE_INDEX = 0;
  private static final List<String> ACTION_IDS = List.of("CompileDirty", "InspectCode");

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
      selectContent(manager, CURRENT_FILE_INDEX);
      window.setAvailable(true, null);
      toolWindowManager.activateToolWindow(window.getId(), null, true, ToolWindowEventSource.InspectionsWidget);
    }
    else if (file.equals(panel.getCurrentFile())) {
      toolWindowManager.hideToolWindow(window.getId(), false, true, ToolWindowEventSource.InspectionsWidget);
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

  private static void createContent(@NotNull ContentManager manager, @NotNull ProblemsViewPanel panel) {
    Content content = manager.getFactory().createContent(panel, panel.getName(0), false);
    content.setCloseable(false);
    manager.addContent(content);
  }

  private static void selectContent(@NotNull ContentManager manager, int index) {
    Content content = manager.getContent(index);
    if (content != null) manager.setSelectedContent(content);
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

  static boolean isProjectErrorsEnabled() {
    return true; // TODO: use this method to disable Project Errors tab in other IDEs
  }

  @Override
  public void init(@NotNull ToolWindow window) {
    if (!isProjectErrorsEnabled()) return;
    Project project = ((ToolWindowEx)window).getProject();
    HighlightingErrorsProviderBase.getInstance(project);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
    ProblemsViewState state = ProblemsViewState.getInstance(project);
    state.setShowToolbar(isToolbarVisible(window, project));
    ContentManager manager = window.getContentManager();
    createContent(manager, new HighlightingPanel(project, state));
    if (isProjectErrorsEnabled()) {
      ProblemsViewPanel panel = new ProblemsViewPanel(project, state, ProblemsViewBundle.messagePointer("problems.view.project"));
      panel.getTreeModel().setRoot(new CollectorBasedRoot(panel));
      StatusText status = panel.getTree().getEmptyText();
      status.setText(ProblemsViewBundle.message("problems.view.project.empty"));
      if (is("ide.problems.view.empty.status.actions")) {
        @NlsSafe String or = ProblemsViewBundle.message("problems.view.project.empty.or");
        int index = 0;
        for (String id : ACTION_IDS) {
          AnAction action = ActionUtil.getAction(id);
          if (action == null) continue;
          @NlsSafe String text = action.getTemplateText();
          if (text == null || text.isBlank()) continue;
          if (index == 0) {
            status.appendText(".");
            status.appendLine("");
          }
          else {
            status.appendText(" ").appendText(or).appendText(" ");
          }
          status.appendText(text, LINK_PLAIN_ATTRIBUTES, event -> {
            ActionUtil.invokeAction(action, panel, "ProblemsView", null, null);
          });
          String shortcut = getFirstKeyboardShortcutText(action);
          if (!shortcut.isBlank()) status.appendText(" (").appendText(shortcut).appendText(")");
          index++;
        }
      }
      createContent(manager, panel);
    }
    selectContent(manager, state.getSelectedIndex());
    selectionChanged(true, manager.getSelectedContent());
    manager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        boolean selected = ContentManagerEvent.ContentOperation.add == event.getOperation();
        ProblemsView.selectionChanged(selected, event.getContent());
        if (selected) state.setSelectedIndex(event.getIndex());
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
