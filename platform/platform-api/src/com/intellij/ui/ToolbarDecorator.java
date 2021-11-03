// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBViewport;
import com.intellij.ui.table.TableView;
import com.intellij.util.SmartList;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 *
 * @see #createDecorator(JList)
 * @see #createDecorator(JTable)
 * @see #createDecorator(JTree)
 */
public abstract class ToolbarDecorator implements CommonActionsPanel.ListenerFactory {
  public static final String DECORATOR_KEY = "ToolbarDecoratorMarker";

  protected Border myPanelBorder;
  protected Border myToolbarBorder;
  protected Border myScrollPaneBorder;
  protected boolean myAddActionEnabled;
  protected boolean myEditActionEnabled;
  protected boolean myRemoveActionEnabled;
  protected boolean myUpActionEnabled;
  protected boolean myDownActionEnabled;
  private final List<AnActionButton> myExtraActions = new SmartList<>();
  private ActionToolbarPosition myToolbarPosition;
  protected AnActionButtonRunnable myAddAction;
  protected AnActionButtonRunnable myEditAction;
  protected AnActionButtonRunnable myRemoveAction;
  protected AnActionButtonRunnable myUpAction;
  protected AnActionButtonRunnable myDownAction;
  private @NlsContexts.Button String myAddName;
  private @NlsContexts.Button String myEditName;
  private @NlsContexts.Button String myRemoveName;
  private @NlsContexts.Button String myMoveUpName;
  private @NlsContexts.Button String myMoveDownName;
  private AnActionButtonUpdater myAddActionUpdater;
  private AnActionButtonUpdater myRemoveActionUpdater;
  private AnActionButtonUpdater myEditActionUpdater;
  private AnActionButtonUpdater myMoveUpActionUpdater;
  private AnActionButtonUpdater myMoveDownActionUpdater;
  private Dimension myPreferredSize;
  private Dimension myMinimumSize;
  private CommonActionsPanel myActionsPanel;
  private Comparator<? super AnActionButton> myButtonComparator;
  private Icon myAddIcon;
  private boolean myForcedDnD;

  @NotNull
  protected abstract JComponent getComponent();

  protected abstract void updateButtons();

  protected void updateExtraElementActions(boolean someElementSelected) {
    for (AnActionButton action : myExtraActions) {
      if (action instanceof ElementActionButton) {
        action.setEnabled(someElementSelected);
      }
    }
  }

  public final CommonActionsPanel getActionsPanel() {
    return myActionsPanel;
  }

  @NotNull
  public ToolbarDecorator initPosition() {
    setToolbarPosition(SystemInfo.isMac && Registry.is("action.toolbar.position.bottom.on.mac")
                       ? ActionToolbarPosition.BOTTOM
                       : ActionToolbarPosition.TOP);
    return this;
  }

  /**
   * @deprecated Equivalent to
   * <pre>{@code decorator
   * .setToolbarPosition(ActionToolbarPosition.TOP)
   * .setPanelBorder(JBUI.Borders.empty())}</pre>
   *
   * @see #setScrollPaneBorder(Border)
   */
  @Deprecated
  public ToolbarDecorator setAsUsualTopToolbar() {
    return setToolbarPosition(ActionToolbarPosition.TOP).setPanelBorder(JBUI.Borders.empty());
  }

  @NotNull
  public static ToolbarDecorator createDecorator(@NotNull JComponent component) {
    return createDecorator(component, null);
  }

  @NotNull
  public static ToolbarDecorator createDecorator(@NotNull JComponent component, @Nullable ElementProducer<?> producer) {
    if (component instanceof JTree) {
      return createDecorator((JTree)component, producer);
    }
    if (component instanceof JTable) {
      if (producer == null) return createDecorator((JTable)component);
      throw new IllegalArgumentException("unexpected producer " + producer.getClass());
    }
    if (component instanceof JList) {
      if (producer == null) return createDecorator((JList<?>)component);
      throw new IllegalArgumentException("unexpected producer " + producer.getClass());
    }
    try {
      Class<?> type = Class.forName("com.intellij.ui.components.JBTreeTable", false, ToolbarDecorator.class.getClassLoader());
      JTree tree = (JTree)type.getDeclaredMethod("getTree").invoke(component);
      return new TreeToolbarDecorator(component, tree, producer).initPosition();
    }
    catch (LinkageError | Exception exception) {
      throw new IllegalArgumentException("unsupported component " + component.getClass(), exception);
    }
  }

  @NotNull
  public static ToolbarDecorator createDecorator(@NotNull JTable table) {
    return new TableToolbarDecorator(table, null).initPosition();
  }

  @NotNull
  public static ToolbarDecorator createDecorator(@NotNull JTree tree) {
    return createDecorator(tree, null);
  }

  @NotNull
  private static ToolbarDecorator createDecorator(@NotNull JTree tree, @Nullable ElementProducer<?> producer) {
    return new TreeToolbarDecorator(tree, producer).initPosition();
  }

  @NotNull
  public static <T> ToolbarDecorator createDecorator(@NotNull JList<T> list) {
    return new ListToolbarDecorator<>(list, null).initPosition();
  }

  @NotNull
  public static <T> ToolbarDecorator createDecorator(@NotNull JList<T> list, EditableModel editableModel) {
    return new ListToolbarDecorator<>(list, editableModel).initPosition();
  }

  @NotNull
  public static <T> ToolbarDecorator  createDecorator(@NotNull TableView<T> table, @Nullable ElementProducer<T> producer) {
    return new TableToolbarDecorator(table, producer).initPosition();
  }

  @NotNull
  public ToolbarDecorator disableAddAction() {
    return setAddAction(null);
  }

  @NotNull
  public ToolbarDecorator disableRemoveAction() {
    return setRemoveAction(null);
  }

  @NotNull
  public ToolbarDecorator disableUpAction() {
    return setMoveUpAction(null);
  }

  @NotNull
  public ToolbarDecorator disableUpDownActions() {
    disableUpAction();
    return disableDownAction();
  }

  @NotNull
  public ToolbarDecorator disableDownAction() {
    return setMoveDownAction(null);
  }

  @NotNull
  public ToolbarDecorator setPanelBorder(Border border) {
    myPanelBorder = border;
    return this;
  }

  @NotNull
  public ToolbarDecorator setToolbarBorder(Border border) {
    myToolbarBorder = border;
    return this;
  }

  @NotNull
  public ToolbarDecorator setScrollPaneBorder(Border border) {
    myScrollPaneBorder = border;
    return this;
  }

  @NotNull
  public ToolbarDecorator setButtonComparator(Comparator<? super AnActionButton> buttonComparator) {
    myButtonComparator = buttonComparator;
    return this;
  }

  @NotNull
  public ToolbarDecorator setButtonComparator(@Nls String @NotNull ...actionNames) {
    final List<String> names = Arrays.asList(actionNames);
    myButtonComparator = (o1, o2) -> {
      final String t1 = o1.getTemplatePresentation().getText();
      final String t2 = o2.getTemplatePresentation().getText();
      if (t1 == null || t2 == null) return 0;

      final int ind1 = names.indexOf(t1);
      final int ind2 = names.indexOf(t2);
      if (ind1 == -1 && ind2 >= 0) return 1;
      if (ind2 == -1 && ind1 >= 0) return -1;
      return ind1 - ind2;
    };
    return this;
  }

  @NotNull
  public ToolbarDecorator addExtraAction(@NotNull AnActionButton action) {
    myExtraActions.add(action);
    return this;
  }

  @NotNull
  public ToolbarDecorator addExtraActions(AnActionButton @NotNull ... actions) {
    for (AnActionButton action : actions) {
      if (action != null) {
        addExtraAction(action);
      }
    }
    return this;
  }

  @NotNull
  public ToolbarDecorator setToolbarPosition(ActionToolbarPosition position) {
    myToolbarPosition = position;
    return this;
  }

  @NotNull
  public ToolbarDecorator setAddAction(AnActionButtonRunnable action) {
    myAddActionEnabled = action != null;
    myAddAction = action;
    return this;
  }

  @NotNull
  public ToolbarDecorator setEditAction(AnActionButtonRunnable action) {
    myEditActionEnabled = action != null;
    myEditAction = action;
    return this;
  }

  @NotNull
  public ToolbarDecorator setRemoveAction(AnActionButtonRunnable action) {
    myRemoveActionEnabled = action != null;
    myRemoveAction = action;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMoveUpAction(AnActionButtonRunnable action) {
    myUpActionEnabled = action != null;
    myUpAction = action;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMoveDownAction(AnActionButtonRunnable action) {
    myDownActionEnabled = action != null;
    myDownAction = action;
    return this;
  }

  @NotNull
  public ToolbarDecorator setAddActionName(@ActionText String name) {
    myAddName = name;
    return this;
  }

  @NotNull
  public ToolbarDecorator setEditActionName(@ActionText String name) {
    myEditName = name;
    return this;
  }

  @NotNull
  public ToolbarDecorator setRemoveActionName(@ActionText String name) {
    myRemoveName = name;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMoveUpActionName(@ActionText String name) {
    myMoveUpName = name;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMoveDownActionName(@ActionText String name) {
    myMoveDownName = name;
    return this;
  }

  @NotNull
  public ToolbarDecorator setAddActionUpdater(AnActionButtonUpdater updater) {
    myAddActionUpdater = updater;
    return this;
  }

  @NotNull
  public ToolbarDecorator setRemoveActionUpdater(AnActionButtonUpdater updater) {
    myRemoveActionUpdater = updater;
    return this;
  }

  @NotNull
  public ToolbarDecorator setEditActionUpdater(AnActionButtonUpdater updater) {
    myEditActionUpdater = updater;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMoveUpActionUpdater(AnActionButtonUpdater updater) {
    myMoveUpActionUpdater = updater;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMoveDownActionUpdater(AnActionButtonUpdater updater) {
    myMoveDownActionUpdater = updater;
    return this;
  }

  @NotNull
  public ToolbarDecorator setForcedDnD() {
    myForcedDnD = true;
    return this;

  }

  @NotNull
  public ToolbarDecorator setActionGroup(@NotNull ActionGroup actionGroup) {
    AnAction[] actions = actionGroup.getChildren(null);
    for (AnAction action : actions) {
      if (!(action instanceof Separator)) {
        addExtraAction(AnActionButton.fromAction(action));
      }
    }
    return this;
  }

  @NotNull
  public ToolbarDecorator setPreferredSize(Dimension size) {
    myPreferredSize = size;
    return this;
  }

  @NotNull
  public ToolbarDecorator setMinimumSize(Dimension size) {
    myMinimumSize = size;
    return this;
  }

  @NotNull
  public ToolbarDecorator setVisibleRowCount(int rowCount) {
    return this;//do nothing by default
  }

  @NotNull
  public ToolbarDecorator setAddIcon(Icon addIcon) {
    myAddIcon = addIcon;
    return this;
  }

  /**
   * @return panel that contains wrapped component (with added scrollpane) and toolbar panel.
   */
  @NotNull
  public JPanel createPanel() {
    CommonActionsPanel.Buttons[] buttons = getButtons();
    JComponent contextComponent = getComponent();
    UIUtil.putClientProperty(contextComponent, JBViewport.FORCE_VISIBLE_ROW_COUNT_KEY, true);
    myActionsPanel = new CommonActionsPanel(this, contextComponent,
                             myToolbarPosition,
                             myExtraActions.toArray(new AnActionButton[0]),
                             myButtonComparator,
                             myAddName, myRemoveName, myMoveUpName, myMoveDownName, myEditName,
                             myAddIcon, buttons);
    JScrollPane scrollPane = null;
    if (contextComponent instanceof JTree || contextComponent instanceof JTable || contextComponent instanceof JList) {
      // add scroll pane to supported components only
      scrollPane = new JBScrollPane(contextComponent) {
        @Override
        public Dimension getPreferredSize() {
          Dimension preferredSize = super.getPreferredSize();
          if (!isPreferredSizeSet()) {
            setPreferredSize(new Dimension(0, preferredSize.height));
          }
          return preferredSize;
        }
      };
      scrollPane.setBorder(JBUI.Borders.empty());
      scrollPane.setViewportBorder(JBUI.Borders.empty());
      if (myPreferredSize != null) {
        scrollPane.setPreferredSize(myPreferredSize);
      }
      if (myMinimumSize != null) {
        scrollPane.setMinimumSize(myMinimumSize);
      }
    }
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        updateButtons();
      }
    };
    panel.add(scrollPane != null ? scrollPane : contextComponent, BorderLayout.CENTER);
    panel.add(myActionsPanel, getPlacement(myToolbarPosition));
    installUpdaters();
    updateButtons();
    installDnD();
    panel.putClientProperty(DECORATOR_KEY, Boolean.TRUE);
    panel.putClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY, myActionsPanel.getComponent(0));

    panel.setBorder(myPanelBorder != null ? myPanelBorder : IdeBorderFactory.createBorder(SideBorder.ALL));
    Border scrollPaneBorder = null;
    if (scrollPane != null && (myScrollPaneBorder != null || myPanelBorder instanceof EmptyBorder)) {
      // if the panel border is empty, the scrollpane shall get one anyway
      scrollPaneBorder = myScrollPaneBorder != null ? myScrollPaneBorder : IdeBorderFactory.createBorder(SideBorder.ALL);
      scrollPane.setBorder(scrollPaneBorder);
    }
    if ((myToolbarBorder == null || myToolbarBorder instanceof EmptyBorder) &&
        scrollPaneBorder == null || scrollPaneBorder instanceof EmptyBorder) {
      myToolbarBorder = new CustomLineBorder(
        myToolbarPosition == ActionToolbarPosition.BOTTOM ? 1 : 0,
        myToolbarPosition == ActionToolbarPosition.RIGHT ? 1 : 0,
        myToolbarPosition == ActionToolbarPosition.TOP ? 1 : 0,
        myToolbarPosition == ActionToolbarPosition.LEFT ? 1 : 0);
    }
    myActionsPanel.setBorder(myToolbarBorder != null ? myToolbarBorder : JBUI.Borders.empty());
    return panel;
  }

  private void installUpdaters() {
    if (myAddActionEnabled && myAddAction != null && myAddActionUpdater != null) {
      myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.ADD).addCustomUpdater(myAddActionUpdater);
    }
    if (myEditActionEnabled && myEditAction != null && myEditActionUpdater != null) {
      myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).addCustomUpdater(myEditActionUpdater);
    }
    if (myRemoveActionEnabled && myRemoveAction != null && myRemoveActionUpdater != null) {
      myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).addCustomUpdater(myRemoveActionUpdater);
    }
    if (myUpActionEnabled && myUpAction != null && myMoveUpActionUpdater != null) {
      myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.UP).addCustomUpdater(myMoveUpActionUpdater);
    }
    if (myDownActionEnabled && myDownAction != null && myMoveDownActionUpdater != null) {
      myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.DOWN).addCustomUpdater(myMoveDownActionUpdater);
    }
  }

  protected void installDnD() {
    if ((myForcedDnD || (myUpAction != null && myUpActionEnabled
        && myDownAction != null && myDownActionEnabled))
        && !ApplicationManager.getApplication().isHeadlessEnvironment()
        && isModelEditable()) {
      installDnDSupport();
    }
  }

  protected abstract void installDnDSupport();

  protected abstract boolean isModelEditable();

  @NotNull
  static Object getPlacement(@NotNull ActionToolbarPosition position) {
    switch (position) {
      case TOP: return BorderLayout.NORTH;
      case LEFT: return BorderLayout.WEST;
      case BOTTOM: return BorderLayout.SOUTH;
      case RIGHT: return BorderLayout.EAST;
    }
    return BorderLayout.SOUTH;
  }

  private CommonActionsPanel.Buttons @NotNull [] getButtons() {
    List<CommonActionsPanel.Buttons> buttons = new ArrayList<>();
    Map<CommonActionsPanel.Buttons, Pair<Boolean, AnActionButtonRunnable>> map = new HashMap<>();
    map.put(CommonActionsPanel.Buttons.ADD, Pair.create(myAddActionEnabled, myAddAction));
    map.put(CommonActionsPanel.Buttons.REMOVE, Pair.create(myRemoveActionEnabled, myRemoveAction));
    map.put(CommonActionsPanel.Buttons.EDIT, Pair.create(myEditActionEnabled, myEditAction));
    map.put(CommonActionsPanel.Buttons.UP, Pair.create(myUpActionEnabled, myUpAction));
    map.put(CommonActionsPanel.Buttons.DOWN, Pair.create(myDownActionEnabled, myDownAction));

    for (CommonActionsPanel.Buttons button : CommonActionsPanel.Buttons.values()) {
      final Pair<Boolean, AnActionButtonRunnable> action = map.get(button);
      if (action != null && action.first != null && action.first && action.second != null) {
        buttons.add(button);
      }
    }

    return buttons.toArray(new CommonActionsPanel.Buttons[0]);
  }

  @Override
  public @NotNull CommonActionsPanel.Listener createListener(@NotNull CommonActionsPanel panel) {
    return new CommonActionsPanel.Listener() {
      @Override
      public void doAdd() {
        if (myAddAction != null) {
          myAddAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.ADD));
        }
      }

      @Override
      public void doEdit() {
        if (myEditAction != null) {
          myEditAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.EDIT));
        }
      }

      @Override
      public void doRemove() {
        if (myRemoveAction != null) {
          myRemoveAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE));
        }
      }

      @Override
      public void doUp() {
        if (myUpAction != null) {
          myUpAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.UP));
        }
      }

      @Override
      public void doDown() {
        if (myDownAction != null) {
          myDownAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.DOWN));
        }
      }
    };
  }

  @Nullable
  public static AnActionButton findAddButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.ADD);
  }

  @Nullable
  public static AnActionButton findEditButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.EDIT);
  }

  @Nullable
  public static AnActionButton findRemoveButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.REMOVE);
  }

  @Nullable
  public static AnActionButton findUpButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.UP);
  }

  @Nullable
  public static AnActionButton findDownButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.DOWN);
  }

  @Nullable
  private static AnActionButton findButton(JComponent comp, CommonActionsPanel.Buttons type) {
    final CommonActionsPanel panel = UIUtil.findComponentOfType(comp, CommonActionsPanel.class);
    if (panel != null) {
      return panel.getAnActionButton(type);
    }
    return null;
  }

  /**
   * Marker interface, button will be disabled if no selected element
   */
  public abstract static class ElementActionButton extends AnActionButton {
    public ElementActionButton(@NlsContexts.Button String text,
                               @NlsContexts.Tooltip String description,
                               @Nullable Icon icon) {
      super(text, description, icon);
    }

    public ElementActionButton(@NlsContexts.Button String text, Icon icon) {
      super(text, icon);
    }

    public ElementActionButton() {
    }

    public ElementActionButton(@NlsContexts.Button String text) {
      super(text);
    }
  }
}
