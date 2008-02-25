package com.intellij.execution.ui.layout;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.execution.ui.layout.impl.RunnerLayout;
import com.intellij.execution.ui.layout.impl.View;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunnerLayoutUi implements Disposable {
  private Project myProject;
  private RunnerLayout myLayout;
  private JPanel myContentPanel;
  private RunnerContentUi myContentUI;

  private ContentManager myViewsContentManager;
  public static final Key<String> CONTENT_TYPE = Key.create("ContentType");

  public RunnerLayoutUi(Project project, Disposable parent, RunnerLayout layout, String runnerTitle, String sessionName) {
    myProject = project;
    myLayout = layout;
    Disposer.register(parent, this);

    myContentUI = new RunnerContentUi(myProject, ActionManager.getInstance(), IdeFocusManager.getInstance(myProject), myLayout,
                                           runnerTitle + " - " + sessionName);


    myContentPanel = new JPanel(new BorderLayout());

    myViewsContentManager = getContentFactory().
      createContentManager(myContentUI.getContentUI(), false, myProject);
    Disposer.register(this, myViewsContentManager);

    myContentPanel.add(myViewsContentManager.getComponent(), BorderLayout.CENTER);
  }

  public RunnerLayoutUi setTopToolbar(@NotNull ActionGroup actions, @NotNull String place) {
    myContentUI.setTopActions(actions, place);
    return this;
  }


  public void addDefaultTab(int id, String text, Icon icon) {
    getLayout().setDefault(id, text, icon);
  }

  public Content addContent(Content content) {
    return addContent(content, false, -1, View.PlaceInGrid.center, false);
  }

  public Content addContent(Content content, int defaultTabId, View.PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    return addContent(content, true, defaultTabId, defaultPlace, defaultIsMinimized);
  }

  private Content addContent(Content content, boolean applyDefaults, int defaultTabId, View.PlaceInGrid defaultPlace, boolean defaultIsMinimized) {
    final String id = content.getUserData(CONTENT_TYPE);

    assert id != null : "Content id is missing, use RunnerLayoutUi to create content instances";

    if (applyDefaults) {
      getLayout().setDefault(id, defaultTabId, defaultPlace, defaultIsMinimized);
    }

    getContentManager().addContent(content);
    return content;
  }

  public Content createContent(String id, JComponent component, String displayName) {
    return createContent(id, component, displayName, null); 
  }

  public Content createContent(String id, JComponent component, String displayName, Icon icon) {
    return createContent(id, component, displayName, icon, null);
  }

  public Content createContent(String id, JComponent component, String displayName, Icon icon, JComponent focusable) {
    final Content content = getContentFactory().createContent(component, displayName, false);
    content.putUserData(CONTENT_TYPE, id);
    content.putUserData(View.ID, id);
    content.setIcon(icon);
    if (focusable != null) {
      content.setPreferredFocusableComponent(focusable);
    }
    return content;
  }


  public JComponent getComponent() {
    return myContentPanel;
  }

  private static ContentFactory getContentFactory() {
    return ContentFactory.SERVICE.getInstance();
  }

  public RunnerLayout getLayout() {
    return myLayout;
  }

  public void updateActionsImmediately() {
    myContentUI.updateActionsImmediately();
  }

  public void dispose() {
  }

  public ContentManager getContentManager() {
    return myViewsContentManager;
  }

  public void setSelected(final Content content) {
    getContentManager().setSelectedContent(content);
  }

  public void removeContent(final Content content, final boolean dispose) {
    getContentManager().removeContent(content, dispose);
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

  public void setLeftToolbar(final DefaultActionGroup leftToolbar, final String place) {
    myContentUI.setLeftToolbar(leftToolbar, place);
  }

  public boolean isActive(final Content content) {
    return getContentManager().isSelected(content);
  }

  @Nullable
  public Content findContent(final String key) {
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

  public void restoreLayout() {
    myContentUI.restoreLayout();
  }

  public void toFront(final String id) {
    final Content content = findContent(id);
    if (content != null) {
      setSelected(content);
    }
  }
}