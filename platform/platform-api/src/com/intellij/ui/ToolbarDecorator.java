/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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

  protected abstract JComponent getComponent();

  protected abstract void updateButtons();

  final CommonActionsPanel getPanel() {
    return myPanel;
  }

  public ToolbarDecorator initPositionAndBorder() {
    myToolbarPosition = UIUtil.isUnderAquaLookAndFeel() ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.RIGHT;
    myBorder = new CustomLineBorder(0,
                                    myToolbarPosition == ActionToolbarPosition.RIGHT ? 1 : 0,
                                    myToolbarPosition == ActionToolbarPosition.TOP ? 1 : 0,
                                    myToolbarPosition == ActionToolbarPosition.LEFT ? 1 : 0);
    final JComponent c = getComponent();
    if (c != null) {
      c.setBorder(IdeBorderFactory.createEmptyBorder(0));
    }
    return this;
  }

  public static ToolbarDecorator createDecorator(@NotNull JTable table) {
    return new TableToolbarDecorator(table, null).initPositionAndBorder();
  }
  
  public static ToolbarDecorator createDecorator(@NotNull JTree tree) {
    return new TreeToolbarDecorator(tree).initPositionAndBorder();
  }

  public static ToolbarDecorator createDecorator(@NotNull JList list) {
    return new ListToolbarDecorator(list).initPositionAndBorder();
  }

  public static <T> ToolbarDecorator  createDecorator(@NotNull TableView<T> table, @Nullable ElementProducer<T> producer) {
    return new TableToolbarDecorator(table, producer).initPositionAndBorder();
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

  public ToolbarDecorator setUpAction(AnActionButtonRunnable action) {
    myUpActionEnabled = action != null;
    myUpAction = action;
    return this;
  }

  public ToolbarDecorator setDownAction(AnActionButtonRunnable action) {
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

  public ToolbarDecorator setPreferredSize(Dimension size) {
    myPreferredSize = size;
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
                             buttons);
    myPanel.setBorder(myBorder);
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
    panel.setBorder(new LineBorder(UIUtil.getBorderColor()));
    panel.putClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY, myPanel.getComponent(0));
    DataManager.registerDataProvider(panel, this);
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
  }

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
    if (myAddActionEnabled && myAddAction != null) {
      buttons.add(CommonActionsPanel.Buttons.ADD);
    }
    if (myEditActionEnabled && myEditAction != null) {
      buttons.add(CommonActionsPanel.Buttons.EDIT);
    }
    if (myRemoveActionEnabled && myRemoveAction != null) {
      buttons.add(CommonActionsPanel.Buttons.REMOVE);
    }
    if (myUpActionEnabled && myUpAction != null) {
      buttons.add(CommonActionsPanel.Buttons.UP);
    }
    if (myDownActionEnabled && myDownAction != null) {
      buttons.add(CommonActionsPanel.Buttons.DOWN);
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
}
