package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.layout.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunnerLayoutUiImpl implements Disposable, RunnerLayoutUi {
   private Project myProject;
  private RunnerLayout myLayout;
  private JPanel myContentPanel;
  private RunnerContentUi myContentUI;

  private ContentManager myViewsContentManager;
  public static final Key<String> CONTENT_TYPE = Key.create("ContentType");

  public RunnerLayoutUiImpl(Project project, Disposable parent, String runnerType, String runnerTitle, String sessionName) {
    myProject = project;
    myLayout = ApplicationManager.getApplication().getComponent(RunnerLayoutSettings.class).getLayout(runnerType);
    Disposer.register(parent, this);

    myContentUI = new RunnerContentUi(myProject, ActionManager.getInstance(), IdeFocusManager.getInstance(myProject), myLayout,
                                           runnerTitle + " - " + sessionName);


    myContentPanel = new JPanel(new BorderLayout());

    myViewsContentManager = getContentFactory().
      createContentManager(myContentUI.getContentUI(), false, myProject);
    Disposer.register(this, myViewsContentManager);

    myContentPanel.add(myViewsContentManager.getComponent(), BorderLayout.CENTER);
  }

  @NotNull
  public RunnerLayoutUi setTopToolbar(@NotNull ActionGroup actions, @NotNull String place) {
    myContentUI.setTopActions(actions, place);
    return this;
  }


  public RunnerLayoutUi initTabDefaults(int id, String text, Icon icon) {
    getLayout().setDefault(id, text, icon);
    return this;
  }

  @NotNull
  public Content addContent(@NotNull Content content) {
    return addContent(content, false, -1, PlaceInGrid.center, false);
  }

  @NotNull
  public Content addContent(@NotNull Content content, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    return addContent(content, true, defaultTabId, defaultPlace, defaultIsMinimized);
  }

  public Content addContent(Content content, boolean applyDefaults, int defaultTabId, PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    final String id = content.getUserData(CONTENT_TYPE);

    assert id != null : "Content id is missing, use RunnerLayoutUi to create content instances";

    if (applyDefaults) {
      getLayout().setDefault(id, defaultTabId, defaultPlace, defaultIsMinimized);
    }

    getContentManager().addContent(content);
    return content;
  }

  @NotNull
  public Content createContent(@NotNull String id, @NotNull JComponent component, @NotNull String displayName, @Nullable Icon icon, @Nullable JComponent focusable) {
    final Content content = getContentFactory().createContent(component, displayName, false);
    content.putUserData(CONTENT_TYPE, id);
    content.putUserData(View.ID, id);
    content.setIcon(icon);
    if (focusable != null) {
      content.setPreferredFocusableComponent(focusable);
    }
    return content;
  }


  @NotNull
  public JComponent getComponent() {
    return myContentPanel;
  }

  private static ContentFactory getContentFactory() {
    return ContentFactory.SERVICE.getInstance();
  }

  public RunnerLayout getLayout() {
    return myLayout;
  }

  public void updateToolbarNow() {
    myContentUI.updateActionsImmediately();
  }

  public void dispose() {
  }

  public ContentManager getContentManager() {
    return myViewsContentManager;
  }

  public void setSelected(final Content content) {
    if (content == null) return;
    getContentManager().setSelectedContent(content);
  }

  public boolean removeContent(final Content content, final boolean dispose) {
    if (content == null) return false;
    return getContentManager().removeContent(content, dispose);
  }

  public void removeContent(String id, final boolean dispose) {
    final Content content = findContent(id);
    if (content != null) {
      getContentManager().removeContent(content, dispose);
    }
  }

  public AnAction getLayoutActions() {
    return myContentUI.getLayoutActions();
  }

  public RunnerLayoutUi setLeftToolbar(@NotNull final ActionGroup leftToolbar, @NotNull final String place) {
    myContentUI.setLeftToolbar(leftToolbar, place);
    return this;
  }

  @Nullable
  public Content findContent(@NotNull final String key) {
    if (myViewsContentManager != null) {
      Content[] contents = myViewsContentManager.getContents();
      for (Content content : contents) {
        String kind = content.getUserData(View.ID);
        if (key.equals(kind)) {
          return content;
        }
      }
    }
    return null;
  }

  public void toFront(@NotNull final String id) {
    final Content content = findContent(id);
    if (content != null) {
      setSelected(content);
    }
  }

  public RunnerLayoutUi addListener(@NotNull final ContentManagerListener listener, @NotNull final Disposable parent) {
    final ContentManager mgr = getContentManager();
    mgr.addContentManagerListener(listener);
    Disposer.register(parent, new Disposable() {
      public void dispose() {
        mgr.removeContentManagerListener(listener);
      }
    });
    return this;
  }

  public void removeListener(@NotNull final ContentManagerListener listener) {
    getContentManager().removeContentManagerListener(listener);
  }

  public boolean isDisposed() {
    return getContentManager().isDisposed();
  }
}
