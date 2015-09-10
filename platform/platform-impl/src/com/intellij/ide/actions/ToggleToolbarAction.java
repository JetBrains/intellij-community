/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
* @author gregsh
*/
public class ToggleToolbarAction extends ToggleAction implements DumbAware {

  @NotNull
  public static ActionGroup createToggleToolbarGroup(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    return new DefaultActionGroup(new OptionsGroup(toolWindow),
                                  new ToggleToolbarAction(toolWindow, PropertiesComponent.getInstance(project)),
                                  Separator.getInstance());
  }

  private final PropertiesComponent myPropertiesComponent;
  private final ToolWindow myToolWindow;

  private ToggleToolbarAction(@NotNull ToolWindow toolWindow, @NotNull PropertiesComponent propertiesComponent) {
    super("Show Toolbar");
    myPropertiesComponent = propertiesComponent;
    myToolWindow = toolWindow;
    myToolWindow.getContentManager().addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void contentAdded(ContentManagerEvent event) {
        JComponent component = event.getContent().getComponent();
        setContentToolbarVisible(component, getVisibilityValue());

        // support nested content managers, e.g. RunnerLayoutUi as content component
        ContentManager contentManager =
          component instanceof DataProvider ? PlatformDataKeys.CONTENT_MANAGER.getData((DataProvider)component) : null;
        if (contentManager != null) contentManager.addContentManagerListener(this);
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean hasToolbars = iterateToolbars(myToolWindow.getContentManager().getComponent()).iterator().hasNext();
    e.getPresentation().setVisible(hasToolbars);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getVisibilityValue();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myPropertiesComponent.setValue(getProperty(), String.valueOf(state), String.valueOf(true));
    for (Content content : myToolWindow.getContentManager().getContents()) {
      setContentToolbarVisible(content.getComponent(), state);
    }
  }

  @NotNull
  private String getProperty() {
    return getShowToolbarProperty(myToolWindow);
  }

  private boolean getVisibilityValue() {
    return myPropertiesComponent.getBoolean(getProperty(), true);
  }

  private static void setContentToolbarVisible(@NotNull JComponent root, boolean state) {
    for (ActionToolbar toolbar : iterateToolbars(root)) {
      toolbar.getComponent().setVisible(state);
    }
  }

  @NotNull
  public static String getShowToolbarProperty(@NotNull ToolWindow window) {
    return "ToolWindow" + window.getStripeTitle() + ".ShowToolbar";
  }

  @NotNull
  private static Iterable<ActionToolbar> iterateToolbars(JComponent root) {
    return UIUtil.uiTraverser().withRoot(root).preOrderDfsTraversal().filter(ActionToolbar.class);
  }

  private static class OptionsGroup extends ActionGroup implements DumbAware {

    private final ToolWindow myToolWindow;

    public OptionsGroup(ToolWindow toolWindow) {
      super("View Options", true);
      myToolWindow = toolWindow;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(!ActionGroupUtil.isGroupEmpty(this, e));
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      ContentManager contentManager = myToolWindow.getContentManager();
      Content selectedContent = contentManager.getSelectedContent();
      JComponent contentComponent = selectedContent != null ? selectedContent.getComponent() : null;
      if (contentComponent == null) return EMPTY_ARRAY;
      List<AnAction> result = ContainerUtil.newSmartList();
      for (final ActionToolbar toolbar : iterateToolbars(contentComponent)) {
        JComponent c = toolbar.getComponent();
        if (c.isVisible() || !c.isValid()) continue;
        if (!result.isEmpty() && !(ContainerUtil.getLastItem(result) instanceof Separator)) {
          result.add(Separator.getInstance());
        }

        List<AnAction> actions = toolbar.getActions(false);
        for (AnAction action : actions) {
          if (action instanceof ToggleAction && !result.contains(action)) {
            result.add(action);
          }
          else if (action instanceof Separator) {
            if (!result.isEmpty() && !(ContainerUtil.getLastItem(result) instanceof Separator)) {
              result.add(Separator.getInstance());
            }
          }
        }
      }
      boolean popup = result.size() > 3;
      setPopup(popup);
      if (!popup && !result.isEmpty()) result.add(Separator.getInstance());
      return result.toArray(new AnAction[result.size()]);
    }
  }
}
