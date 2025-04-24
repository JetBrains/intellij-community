// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.impl.EditorHeaderComponent;
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.impl.ContentManagerImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author gregsh
 */
public final class ToggleToolbarAction extends ToggleAction implements DumbAware {
  public static @NotNull DefaultActionGroup createToggleToolbarGroup(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    return new DefaultActionGroup(new OptionsGroup(toolWindow),
                                  createToolWindowAction(toolWindow, PropertiesComponent.getInstance(project)));
  }

  public static @NotNull ToggleToolbarAction createAction(@NotNull String id,
                                                          @NotNull PropertiesComponent properties,
                                                          @NotNull Supplier<? extends Iterable<? extends JComponent>> components) {
    return new ToggleToolbarAction(properties, getShowToolbarProperty(id), components);
  }

  public static @NotNull ToggleToolbarAction createToolWindowAction(@NotNull ToolWindow toolWindow,
                                                                    @NotNull PropertiesComponent properties) {
    updateToolbarsVisibility(toolWindow, properties);
    toolWindow.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void contentAdded(@NotNull ContentManagerEvent event) {
        JComponent component = event.getContent().getComponent();
        setToolbarVisible(Collections.singletonList(component), isToolbarVisible(toolWindow, properties));

        // support nested content managers, e.g. RunnerLayoutUi as content component
        ContentManager contentManager = ContentManagerImpl.getContentManager(component);
        if (contentManager != null) {
          contentManager.addContentManagerListener(this);
        }
      }

      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        if (event.getOperation() != ContentManagerEvent.ContentOperation.remove) {
          updateToolbarsVisibility(toolWindow, properties);
        }
      }
    });
    return new ToggleToolbarAction(properties, getShowToolbarProperty(toolWindow), () -> {
      ContentManager manager = toolWindow.getContentManagerIfCreated();
      return ContainerUtil.createMaybeSingletonList(manager == null ? null : manager.getComponent());
    });
  }

  private static void updateToolbarsVisibility(@NotNull ToolWindow toolWindow,
                                               @NotNull PropertiesComponent properties) {
    if (toolWindow.getContentManagerIfCreated() == null) return;
    setToolbarVisible(Collections.singletonList(toolWindow.getComponent()), isToolbarVisible(toolWindow, properties));
  }

  public static void updateToolbarVisibility(@NotNull ToolWindow toolWindow,
                                             @NotNull ActionToolbar toolbar,
                                             @NotNull PropertiesComponent properties) {
    setToolbarVisible(toolbar, isToolbarVisible(toolWindow, properties));
  }

  public static void setToolbarVisible(@NotNull ToolWindow toolWindow,
                                       @NotNull PropertiesComponent properties,
                                       @Nullable Boolean visible) {
    boolean state = visible == null ? isToolbarVisible(toolWindow, properties) : visible;
    setToolbarVisibleImpl(getShowToolbarProperty(toolWindow), properties, Collections.singletonList(toolWindow.getComponent()), state);
  }

  public static void setToolbarVisible(@NotNull String id,
                                       @NotNull PropertiesComponent properties,
                                       @NotNull Iterable<? extends JComponent> components,
                                       @Nullable Boolean visible) {
    boolean state = visible == null ? isToolbarVisible(id, properties) : visible;
    setToolbarVisibleImpl(getShowToolbarProperty(id), properties, components, state);
  }

  public static void setToolbarVisible(@NotNull Iterable<? extends JComponent> roots,
                                       boolean state) {
    for (ActionToolbar toolbar : iterateToolbars(roots)) {
      setToolbarVisible(toolbar, state);
    }
  }

  private static void setToolbarVisible(ActionToolbar toolbar, boolean state) {
    JComponent c = toolbar.getComponent();
    c.setVisible(state);
    Container parent = c.getParent();
    if (parent instanceof EditorHeaderComponent) {
      parent.setVisible(state);
    }
  }

  public static boolean isToolbarVisible(@NotNull String property) {
    return isToolbarVisible(property, PropertiesComponent.getInstance());
  }

  public static boolean isToolbarVisible(@NotNull String property, @NotNull Project project) {
    return isToolbarVisible(property, PropertiesComponent.getInstance(project));
  }

  public static boolean isToolbarVisible(@NotNull String property, @NotNull PropertiesComponent properties) {
    return isSelectedImpl(properties, getShowToolbarProperty(property));
  }

  public static boolean isToolbarVisible(@NotNull ToolWindow toolWindow) {
    return isToolbarVisible(toolWindow, PropertiesComponent.getInstance());
  }

  public static boolean isToolbarVisible(@NotNull ToolWindow toolWindow, @NotNull Project project) {
    return isToolbarVisible(toolWindow, PropertiesComponent.getInstance(project));
  }

  public static boolean isToolbarVisible(@NotNull ToolWindow toolWindow, @NotNull PropertiesComponent properties) {
    return isSelectedImpl(properties, getShowToolbarProperty(toolWindow));
  }

  private final PropertiesComponent myPropertiesComponent;
  private final String myProperty;
  private final Supplier<? extends Iterable<? extends JComponent>> myProducer;

  private ToggleToolbarAction(@NotNull PropertiesComponent propertiesComponent,
                              @NotNull String property,
                              @NotNull Supplier<? extends Iterable<? extends JComponent>> producer) {
    super(ActionsBundle.messagePointer("action.ShowToolbar.text"));
    myPropertiesComponent = propertiesComponent;
    myProperty = property;
    myProducer = producer;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    boolean hasToolbars = iterateToolbars(myProducer.get()).iterator().hasNext();
    e.getPresentation().setVisible(hasToolbars);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return isSelected();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    setToolbarVisibleImpl(myProperty, myPropertiesComponent, myProducer.get(), state);
  }

  static void setToolbarVisibleImpl(@NotNull String property,
                                    @NotNull PropertiesComponent propertiesComponent,
                                    @NotNull Iterable<? extends JComponent> components,
                                    boolean visible) {
    propertiesComponent.setValue(property, String.valueOf(visible), String.valueOf(true));
    setToolbarVisible(components, visible);
  }

  boolean isSelected() {
    return isSelectedImpl(myPropertiesComponent, myProperty);
  }

  static boolean isSelectedImpl(@NotNull PropertiesComponent properties, @NotNull String property) {
    return properties.getBoolean(property, true);
  }

  static @NotNull String getShowToolbarProperty(@NotNull ToolWindow window) {
    return getShowToolbarProperty("ToolWindow." + window.getId());
  }

  static @NotNull String getShowToolbarProperty(@NotNull @NonNls String s) {
    return s + ".ShowToolbar";
  }

  private static @NotNull Iterable<ActionToolbar> iterateToolbars(Iterable<? extends JComponent> roots) {
    return UIUtil.uiTraverser(null).withRoots(roots).preOrderDfsTraversal()
      .filter(ActionToolbar.class)
      .filter(toolbar -> {
        var c = toolbar.getComponent();
        return !Boolean.TRUE.equals(c.getClientProperty(ActionToolbarImpl.IMPORTANT_TOOLBAR_KEY)) &&
               !(c instanceof FloatingToolbarComponent);
      });
  }

  private static final class OptionsGroup extends NonTrivialActionGroup implements DumbAware {

    private final ToolWindow myToolWindow;

    OptionsGroup(ToolWindow toolWindow) {
      getTemplatePresentation().setText(IdeBundle.message("group.view.options"));
      myToolWindow = toolWindow;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isVisible()) {
        int trimmedSize = ActionGroupUtil.getVisibleActions(this, e).take(4).size();
        e.getPresentation().setPopupGroup(trimmedSize > 3);
      }
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      if (e == null) return EMPTY_ARRAY;
      UpdateSession updateSession = e.getUpdateSession();
      List<ActionGroup> groups = updateSession.compute(
        this, "collectActionGroups", ActionUpdateThread.EDT, this::collectActionGroups);
      if (groups.isEmpty()) return EMPTY_ARRAY;
      List<AnAction> result = new ArrayList<>();
      for (ActionGroup group : groups) {
        Iterable<? extends AnAction> actions = updateSession.expandedChildren(group);
        for (AnAction action : actions) {
          if (action instanceof ToggleAction && !result.contains(action)) {
            result.add(action);
          }
          else if (action instanceof Separator && !result.isEmpty() &&
                   !(ContainerUtil.getLastItem(result) instanceof Separator)) {
            result.add(Separator.getInstance());
          }
        }
      }
      boolean popup = ContainerUtil.count(result, it -> !(it instanceof Separator)) > 3;
      if (!popup && !result.isEmpty()) result.add(Separator.getInstance());
      return result.toArray(EMPTY_ARRAY);
    }

    private @Unmodifiable @NotNull List<ActionGroup> collectActionGroups() {
      ContentManager contentManager = myToolWindow.getContentManagerIfCreated();
      Content selectedContent = contentManager == null ? null : contentManager.getSelectedContent();
      JComponent contentComponent = selectedContent == null ? null : selectedContent.getComponent();
      if (contentComponent == null) return Collections.emptyList();
      return JBIterable.from(iterateToolbars(Collections.singletonList(contentComponent)))
        .filterMap(toolbar -> {
          JComponent c = toolbar.getComponent();
          if (c.isVisible() || !c.isValid()) return null;
          return toolbar.getActionGroup();
        })
        .toList();
    }
  }
}
