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
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class ToolbarDecorator implements DataProvider, CommonActionsPanel.ListenerFactory {
  private static final Comparator<AnAction> ACTION_BUTTONS_SORTER = new Comparator<AnAction>() {
    @Override
    public int compare(AnAction a1, AnAction a2) {
      if (a1 instanceof AnActionButton && a2 instanceof AnActionButton) {
        final JComponent c1 = ((AnActionButton)a1).getContextComponent();
        final JComponent c2 = ((AnActionButton)a2).getContextComponent();
        return c1.hasFocus() ? -1 : c2.hasFocus() ? 1 : 0;
      }
      return 0;
    }
  };

  protected Border myToolbarBorder;
  protected boolean myAddActionEnabled;
  protected boolean myEditActionEnabled;
  protected boolean myRemoveActionEnabled;
  protected boolean myUpActionEnabled;
  protected boolean myDownActionEnabled;
  protected Border myBorder;
  private List<AnActionButton> myExtraActions = new ArrayList<AnActionButton>();
  private ActionToolbarPosition myToolbarPosition;
  protected AnActionButtonRunnable myAddAction;
  protected AnActionButtonRunnable myEditAction;
  protected AnActionButtonRunnable myRemoveAction;
  protected AnActionButtonRunnable myUpAction;
  protected AnActionButtonRunnable myDownAction;
  private String myAddName;
  private String myEditName;
  private String myRemoveName;
  private String myMoveUpName;
  private String myMoveDownName;
  private AnActionButtonUpdater myAddActionUpdater = null;
  private AnActionButtonUpdater myRemoveActionUpdater = null;
  private AnActionButtonUpdater myEditActionUpdater = null;
  private AnActionButtonUpdater myMoveUpActionUpdater = null;
  private AnActionButtonUpdater myMoveDownActionUpdater = null;
  private Dimension myPreferredSize;
  private CommonActionsPanel myPanel;
  private Comparator<AnActionButton> myButtonComparator;
  private boolean myAsTopToolbar = false;
  private Icon myAddIcon;
  private boolean myForcedDnD = false;

  protected abstract JComponent getComponent();

  protected abstract void updateButtons();

  public final CommonActionsPanel getPanel() {
    return myPanel;
  }

  public ToolbarDecorator initPosition() {
    setToolbarPosition(SystemInfo.isMac ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.RIGHT);
    return this;
  }

  public ToolbarDecorator setAsTopToolbar() {
    myAsTopToolbar = true;
    setToolbarPosition(ActionToolbarPosition.TOP);
    return this;
  }

  public static ToolbarDecorator createDecorator(@NotNull JTable table) {
    return new TableToolbarDecorator(table, null).initPosition();
  }
  
  public static ToolbarDecorator createDecorator(@NotNull JTree tree) {
    return createDecorator(tree, null);
  }

  private static ToolbarDecorator createDecorator(@Nullable JTree tree, @Nullable ElementProducer<?> producer) {
    return new TreeToolbarDecorator(tree, producer).initPosition();
  }

  public static ToolbarDecorator createDecorator(@NotNull JList list) {
    return new ListToolbarDecorator(list).initPosition();
  }

  public static <T> ToolbarDecorator  createDecorator(@NotNull TableView<T> table, @Nullable ElementProducer<T> producer) {
    return new TableToolbarDecorator(table, producer).initPosition();
  }

  public ToolbarDecorator disableAddAction() {
    myAddActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableRemoveAction() {
    myRemoveActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableUpAction() {
    myUpActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableUpDownActions() {
    myUpActionEnabled = false;
    myDownActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableDownAction() {
    myDownActionEnabled = false;
    return this;
  }

  public ToolbarDecorator setToolbarBorder(Border border) {
    myBorder = border;
    return this;
  }

  public ToolbarDecorator setButtonComparator(Comparator<AnActionButton> buttonComparator) {
    myButtonComparator = buttonComparator;
    return this;
  }
  
  public ToolbarDecorator setButtonComparator(String...actionNames) {
    final List<String> names = Arrays.asList(actionNames);
    myButtonComparator = new Comparator<AnActionButton>() {
      @Override
      public int compare(AnActionButton o1, AnActionButton o2) {
        final String t1 = o1.getTemplatePresentation().getText();
        final String t2 = o2.getTemplatePresentation().getText();
        if (t1 == null || t2 == null) return 0;
        
        final int ind1 = names.indexOf(t1);
        final int ind2 = names.indexOf(t2);
        if (ind1 == -1 && ind2 >= 0) return 1;
        if (ind2 == -1 && ind1 >= 0) return -1;
        return ind1 - ind2;
      }
    };
    return this;
  }

  public ToolbarDecorator setLineBorder(int top, int left, int bottom, int right) {
    return setToolbarBorder(new CustomLineBorder(top, left, bottom, right));
  }

  public ToolbarDecorator addExtraAction(AnActionButton action) {
    myExtraActions.add(action);
    return this;
  }

  public ToolbarDecorator setToolbarPosition(ActionToolbarPosition position) {
    myToolbarPosition = position;
    myBorder = new CustomLineBorder(0,
                                    myToolbarPosition == ActionToolbarPosition.RIGHT ? 1 : 0,
                                    myToolbarPosition == ActionToolbarPosition.TOP ? 1 : 0,
                                    myToolbarPosition == ActionToolbarPosition.LEFT ? 1 : 0);
    return this;
  }

  public ToolbarDecorator setAddAction(AnActionButtonRunnable action) {
    myAddActionEnabled = action != null;
    myAddAction = action;
    return this;
  }

  public ToolbarDecorator setEditAction(AnActionButtonRunnable action) {
    myEditActionEnabled = action != null;
    myEditAction = action;
    return this;
  }

  public ToolbarDecorator setRemoveAction(AnActionButtonRunnable action) {
    myRemoveActionEnabled = action != null;
    myRemoveAction = action;
    return this;
  }

  public ToolbarDecorator setMoveUpAction(AnActionButtonRunnable action) {
    myUpActionEnabled = action != null;
    myUpAction = action;
    return this;
  }

  public ToolbarDecorator setMoveDownAction(AnActionButtonRunnable action) {
    myDownActionEnabled = action != null;
    myDownAction = action;
    return this;
  }

  public ToolbarDecorator setAddActionName(String name) {
    myAddName = name;
    return this;
  }

  public ToolbarDecorator setEditActionName(String name) {
    myEditName = name;
    return this;
  }

  public ToolbarDecorator setRemoveActionName(String name) {
    myRemoveName = name;
    return this;
  }

  public ToolbarDecorator setMoveUpActionName(String name) {
    myMoveUpName = name;
    return this;
  }

  public ToolbarDecorator setMoveDownActionName(String name) {
    myMoveDownName = name;
    return this;
  }

  public ToolbarDecorator setAddActionUpdater(AnActionButtonUpdater updater) {
    myAddActionUpdater = updater;
    return this;
  }

  public ToolbarDecorator setRemoveActionUpdater(AnActionButtonUpdater updater) {
    myRemoveActionUpdater = updater;
    return this;
  }

  public ToolbarDecorator setEditActionUpdater(AnActionButtonUpdater updater) {
    myEditActionUpdater = updater;
    return this;
  }

  public ToolbarDecorator setMoveUpActionUpdater(AnActionButtonUpdater updater) {
    myMoveUpActionUpdater = updater;
    return this;
  }

  public ToolbarDecorator setMoveDownActionUpdater(AnActionButtonUpdater updater) {
    myMoveDownActionUpdater = updater;
    return this;
  }

  public ToolbarDecorator setForcedDnD() {
    myForcedDnD = true;
    return this;

  }

  public ToolbarDecorator setActionGroup(@NotNull ActionGroup actionGroup) {
    AnAction[] actions = actionGroup.getChildren(null);
    for (AnAction action : actions) {
      addExtraAction(AnActionButton.fromAction(action));
    }
    return this;
  }

  public ToolbarDecorator setPreferredSize(Dimension size) {
    myPreferredSize = size;
    return this;
  }

  public ToolbarDecorator setVisibleRowCount(int rowCount) {
    return this;//do nothing by default
  }

  public ToolbarDecorator setAddIcon(Icon addIcon) {
    myAddIcon = addIcon;
    return this;
  }

  public JPanel createPanel() {
    final CommonActionsPanel.Buttons[] buttons = getButtons();
    final JComponent contextComponent = getComponent();
    myPanel = new CommonActionsPanel(this, contextComponent,
                             myToolbarPosition == ActionToolbarPosition.TOP || myToolbarPosition == ActionToolbarPosition.BOTTOM,
                             myExtraActions.toArray(new AnActionButton[myExtraActions.size()]),
                             myButtonComparator,
                             myAddName, myRemoveName, myMoveUpName, myMoveDownName, myEditName,
                             myAddIcon, buttons);
    if (myAsTopToolbar) {
      contextComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL));
    } else {
      myPanel.setBorder(myBorder);
    }
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(contextComponent);
    if (myPreferredSize != null) {
      scrollPane.setPreferredSize(myPreferredSize);
    }
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder(0));
    final JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        updateButtons();
      }
    };
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(myPanel, getPlacement());
    installUpdaters();
    updateButtons();
    installDnD();
    panel.putClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY, myPanel.getComponent(0));
    DataManager.registerDataProvider(panel, this);
    if (myAsTopToolbar) {
      panel.setBorder(null);
      if (getComponent().getBorder() == null) {
        getComponent().setBorder(IdeBorderFactory.createBorder(SideBorder.ALL));
      }
    } else {
      panel.setBorder(new LineBorder(UIUtil.getBorderColor()));
      final JComponent c = getComponent();
      if (contextComponent != null) {
        contextComponent.setBorder(IdeBorderFactory.createEmptyBorder(0));
      }
    }
    return panel;
  }

  private void installUpdaters() {
    if (myAddActionEnabled && myAddAction != null && myAddActionUpdater != null) {
      myPanel.getAnActionButton(CommonActionsPanel.Buttons.ADD).addCustomUpdater(myAddActionUpdater);
    }
    if (myEditActionEnabled && myEditAction != null && myEditActionUpdater != null) {
      myPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).addCustomUpdater(myEditActionUpdater);
    }
    if (myRemoveActionEnabled && myRemoveAction != null && myRemoveActionUpdater != null) {
      myPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).addCustomUpdater(myRemoveActionUpdater);
    }
    if (myUpActionEnabled && myUpAction != null && myMoveUpActionUpdater != null) {
      myPanel.getAnActionButton(CommonActionsPanel.Buttons.UP).addCustomUpdater(myMoveUpActionUpdater);
    }
    if (myDownActionEnabled && myDownAction != null && myMoveDownActionUpdater != null) {
      myPanel.getAnActionButton(CommonActionsPanel.Buttons.DOWN).addCustomUpdater(myMoveDownActionUpdater);
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

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.ACTIONS_SORTER.is(dataId)) {
      return ACTION_BUTTONS_SORTER;
    }
    return null;
  }

  private Object getPlacement() {
    switch (myToolbarPosition) {
      case TOP: return BorderLayout.NORTH;
      case LEFT: return BorderLayout.WEST;
      case BOTTOM: return BorderLayout.SOUTH;
      case RIGHT: return BorderLayout.EAST;
    }
    return BorderLayout.SOUTH;
  }

  private CommonActionsPanel.Buttons[] getButtons() {
    final ArrayList<CommonActionsPanel.Buttons> buttons = new ArrayList<CommonActionsPanel.Buttons>();
    final HashMap<CommonActionsPanel.Buttons, Pair<Boolean, AnActionButtonRunnable>> map =
      new HashMap<CommonActionsPanel.Buttons, Pair<Boolean, AnActionButtonRunnable>>();
    map.put(CommonActionsPanel.Buttons.ADD, Pair.create(myAddActionEnabled, myAddAction));
    map.put(CommonActionsPanel.Buttons.REMOVE, Pair.create(myRemoveActionEnabled, myRemoveAction));
    map.put(CommonActionsPanel.Buttons.EDIT, Pair.create(myEditActionEnabled, myEditAction));
    map.put(CommonActionsPanel.Buttons.UP, Pair.create(myUpActionEnabled, myUpAction));
    map.put(CommonActionsPanel.Buttons.DOWN, Pair.create(myDownActionEnabled, myDownAction));

    for (CommonActionsPanel.Buttons button : CommonActionsPanel.Buttons.values()) {
      final Pair<Boolean, AnActionButtonRunnable> action = map.get(button);
      if (action != null && action.first && action.second != null) {
        buttons.add(button);
      }
    }

    return buttons.toArray(new CommonActionsPanel.Buttons[buttons.size()]);
  }

  public CommonActionsPanel.Listener createListener(final CommonActionsPanel panel) {
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
  
  public static AnActionButton findAddButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.ADD);
  }

  public static AnActionButton findEditButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.EDIT);
  }

  public static AnActionButton findRemoveButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.REMOVE);
  }

  public static AnActionButton findUpButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.UP);
  }

  public static AnActionButton findDownButton(@NotNull JComponent container) {
    return findButton(container, CommonActionsPanel.Buttons.DOWN);
  }

  private static AnActionButton findButton(JComponent comp, CommonActionsPanel.Buttons type) {
    final CommonActionsPanel panel = UIUtil.findComponentOfType(comp, CommonActionsPanel.class);
    if (panel != null) {
      return panel.getAnActionButton(type);
    }
    //noinspection ConstantConditions
    return null;
  }
}
