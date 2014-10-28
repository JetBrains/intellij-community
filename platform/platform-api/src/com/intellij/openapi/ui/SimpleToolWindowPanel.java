/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.switcher.QuickActionProvider;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.LinkedList;
import java.util.List;

public class SimpleToolWindowPanel extends JPanel implements QuickActionProvider, DataProvider {

  private JComponent myToolbar;
  private JComponent myContent;

  private boolean myBorderless;
  protected boolean myVertical;
  private boolean myProvideQuickActions;

  public SimpleToolWindowPanel(boolean vertical) {
    this(vertical, false);
  }

  public SimpleToolWindowPanel(boolean vertical, boolean borderless) {
    setLayout(new BorderLayout(vertical ? 0 : 1, vertical ? 1 : 0));
    myBorderless = borderless;
    myVertical = vertical;
    setProvideQuickActions(true);

    addContainerListener(new ContainerAdapter() {
      @Override
      public void componentAdded(ContainerEvent e) {
        Component child = e.getChild();

        if (child instanceof Container) {
          ((Container)child).addContainerListener(this);
        }
        if (myBorderless) {
          UIUtil.removeScrollBorder(SimpleToolWindowPanel.this);
        }
      }

      @Override
      public void componentRemoved(ContainerEvent e) {
        Component child = e.getChild();
        
        if (child instanceof Container) {
          ((Container)child).removeContainerListener(this);
        }
      }
    });
  }

  public boolean isToolbarVisible() {
    return myToolbar != null && myToolbar.isVisible();
  }

  public void setToolbar(@Nullable JComponent c) {
    if (c == null) {
      remove(myToolbar);
    }
    myToolbar = c;

    if (c != null) {
      if (myVertical) {
        add(c, BorderLayout.NORTH);
      } else {
        add(c, BorderLayout.WEST);
      }
    }

    revalidate();
    repaint();
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    return QuickActionProvider.KEY.is(dataId) && myProvideQuickActions ? this : null;
  }

  public SimpleToolWindowPanel setProvideQuickActions(boolean provide) {
    myProvideQuickActions = provide;
    return this;
  }

  public List<AnAction> getActions(boolean originalProvider) {
    final Ref<ActionToolbar> toolbar = new Ref<ActionToolbar>();
    if (myToolbar != null) {
      new AwtVisitor(myToolbar) {
        @Override
        public boolean visit(Component component) {
          if (component instanceof ActionToolbar) {
            toolbar.set((ActionToolbar)component);
            return true;
          }
          return false;
        }
      };
    }

    if (toolbar.get() != null) {
      return toolbar.get().getActions(originalProvider);
    }

    return null;
  }

  public JComponent getComponent() {
    return this;
  }

  public boolean isCycleRoot() {
    return false;
  }

  public void setContent(JComponent c) {
    myContent = c;
    add(c, BorderLayout.CENTER);

    if (myBorderless) {
      UIUtil.removeScrollBorder(c);
    }

    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);

    if (myToolbar != null && myToolbar.getParent() == this && myContent != null && myContent.getParent() == this) {
      g.setColor(UIUtil.getBorderColor());
      if (myVertical) {
        final int y = (int)myToolbar.getBounds().getMaxY();
        g.drawLine(0, y, getWidth(), y);
      } else {
        int x = (int)myToolbar.getBounds().getMaxX();
        g.drawLine(x, 0, x, getHeight());
      }
    }
  }

  @NotNull
  public static AnAction createToggleToolbarAction(Project project, @NotNull ToolWindow toolWindow) {
    return new ToggleToolbarAction(toolWindow, PropertiesComponent.getInstance(project));
  }

  private static class ToggleToolbarAction extends ToggleAction implements DumbAware {

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

        @Override
        public void selectionChanged(ContentManagerEvent event) {
          Content content = event.getContent();
          if (content != null) {
            setContentToolbarVisible(content.getComponent(), getVisibilityValue());
          }
        }
      });
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
      return myToolWindow.getStripeTitle() + ".ShowToolbar";
    }

    private boolean getVisibilityValue() {
      return myPropertiesComponent.getBoolean(getProperty(), true);
    }

    private static void setContentToolbarVisible(@NotNull JComponent root, boolean state) {
      LinkedList<JComponent> deque = ContainerUtil.newLinkedList(root);
      while(!deque.isEmpty()) {
        JComponent component = deque.pollFirst();
        for (int i = 0, count = component.getComponentCount(); i < count; i++) {
          Component c = component.getComponent(i);
          if (c instanceof ActionToolbar) {
            c.setVisible(state);
          }
          else if (c instanceof JPanel || c instanceof JLayeredPane || c instanceof JBTabs) {
            deque.addLast((JComponent)c);
          }
        }
      }
    }
  }
}