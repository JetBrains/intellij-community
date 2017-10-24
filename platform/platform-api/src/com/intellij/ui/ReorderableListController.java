/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author dyoma
 */
public abstract class ReorderableListController <T> {
  private final JList myList;
  private static final Icon REMOVE_ICON = IconUtil.getRemoveIcon();

  protected ReorderableListController(final JList list) {
    myList = list;
  }

  public JList getList() {
    return myList;
  }

  public RemoveActionDescription addRemoveAction(final String actionName) {
    final RemoveActionDescription description = new RemoveActionDescription(actionName);
    addActionDescription(description);
    return description;
  }

  protected abstract void addActionDescription(ActionDescription description);

  public AddActionDescription addAddAction(final String actionName, final Factory<T> creator, final boolean createShortcut) {
    final AddActionDescription description = new AddActionDescription(actionName, creator, createShortcut);
    addActionDescription(description);
    return description;
  }

  public AddMultipleActionDescription addAddMultipleAction(final String actionName, final Factory<Collection<T>> creator, final boolean createShortcut) {
    final AddMultipleActionDescription description = new AddMultipleActionDescription(actionName, creator, createShortcut);
    addActionDescription(description);
    return description;
  }

  public CopyActionDescription addCopyAction(final String actionName, final Convertor<T, T> copier, final Condition<T> enableCondition) {
    final CopyActionDescription description = new CopyActionDescription(actionName, copier, enableCondition);
    addActionDescription(description);
    return description;
  }

  public void addMoveUpAction() {
    addAction(new AnAction(UIBundle.message("move.up.action.name"), null, IconUtil.getMoveUpIcon()) {
      public void actionPerformed(final AnActionEvent e) {
        ListUtil.moveSelectedItemsUp(myList);
      }

      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(ListUtil.canMoveSelectedItemsUp(myList));
      }
    });
  }

  public void addMoveDownAction() {
    addAction(new AnAction(UIBundle.message("move.down.action.name"), null, AllIcons.Actions.MoveDown) {
      public void actionPerformed(final AnActionEvent e) {
        ListUtil.moveSelectedItemsDown(myList);
      }

      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(ListUtil.canMoveSelectedItemsDown(myList));
      }
    });
  }

  public void addAction(final AnAction action) {
    addActionDescription(new FixedActionDescription(action));
  }

  private void handleNewElement(final T element) {
    final ListModel listModel = myList.getModel();
    if (listModel instanceof SortedListModel) {
      ((SortedListModel<T>)listModel).add(element);
    }
    else {
      ((DefaultListModel)listModel).addElement(element);
    }
    myList.clearSelection();
    ScrollingUtil.selectItem(myList, element);
  }

  public static <T> ReorderableListController<T> create(final JList list, final DefaultActionGroup actionGroup) {
    return new ReorderableListController<T>(list) {
      protected void addActionDescription(final ActionDescription description) {
        actionGroup.add(description.createAction(list));
      }
    };
  }

  protected static abstract class ActionDescription {
    public abstract AnAction createAction(JComponent component);
  }

  public interface ActionNotification <T> {
    void afterActionPerformed(T change);
  }

  public static abstract class CustomActionDescription <V> extends ActionDescription {
    private final ArrayList<ActionNotification<V>> myPostHandlers = new ArrayList<>(1);
    private boolean myShowText = false;

    public void addPostHandler(final ActionNotification<V> runnable) {
      myPostHandlers.add(runnable);
    }

    protected void runPostHandlers(final V change) {
      for (Iterator<ActionNotification<V>> iterator = myPostHandlers.iterator(); iterator.hasNext();) {
        final ActionNotification<V> runnable = iterator.next();
        runnable.afterActionPerformed(change);
      }
    }

    public abstract CustomActionDescription.BaseAction createAction(JComponent component);

    BaseAction createAction(final ActionBehaviour behaviour) {
      return myShowText ?
             new ActionWithText(this, getActionName(), null, getActionIcon(), behaviour) :
             new BaseAction(this, getActionName(), null, getActionIcon(), behaviour);
    }

    protected abstract Icon getActionIcon();

    protected abstract String getActionName();

    public void setShowText(final boolean showText) {
      myShowText = showText;
    }

    protected static class BaseAction<V> extends DumbAwareAction {
      private final ActionBehaviour<V> myBehaviour;
      private final CustomActionDescription<V> myCustomActionDescription;

      public BaseAction(final CustomActionDescription<V> customActionDescription,
                        final String text, final String description, final Icon icon, final ActionBehaviour<V> behaviour) {
        super(text, description, icon);
        myBehaviour = behaviour;
        this.myCustomActionDescription = customActionDescription;
      }

      public void actionPerformed(final AnActionEvent e) {
        final V change = myBehaviour.performAction(e);
        if (change == null) return;
        myCustomActionDescription.runPostHandlers(change);
      }

      public void update(final AnActionEvent e) {
        myBehaviour.updateAction(e);
      }
    }

    private static class ActionWithText<V> extends BaseAction  {
      public ActionWithText(final CustomActionDescription<V> customActionDescription, final String text,
                            final String description,
                            final Icon icon,
                            final ActionBehaviour<V> behaviour) {
        super(customActionDescription, text, description, icon, behaviour);
      }

      @Override
      public boolean displayTextInToolbar() {
        return true;
      }
    }
  }

  static interface ActionBehaviour<T> {
    T performAction(AnActionEvent e);
    void updateAction(AnActionEvent e);
  }

  public class RemoveActionDescription extends CustomActionDescription<List<T>> {
    private final String myActionName;
    private Condition<List<T>> myConfirmation;
    private Condition<T> myEnableCondition;

    public RemoveActionDescription(final String actionName) {
      myActionName = actionName;
    }

    public BaseAction createAction(final JComponent component) {
      final ActionBehaviour<List<T>> behaviour = new ActionBehaviour<List<T>>() {
        public List<T> performAction(final AnActionEvent e) {
          if (myConfirmation != null && !myConfirmation.value((List<T>)Arrays.asList(myList.getSelectedValues()))) {
            return Collections.emptyList();
          }
          return ListUtil.removeSelectedItems(myList, myEnableCondition);
        }

        public void updateAction(final AnActionEvent e) {
          e.getPresentation().setEnabled(ListUtil.canRemoveSelectedItems(myList, myEnableCondition));
        }
      };
      final BaseAction action = createAction(behaviour);
      action.registerCustomShortcutSet(CommonShortcuts.getDelete(), component);
      return action;
    }

    protected Icon getActionIcon() {
      return REMOVE_ICON;
    }

    protected String getActionName() {
      return myActionName;
    }

    public void setConfirmation(final Condition<List<T>> confirmation) {
      myConfirmation = confirmation;
    }

    public void setEnableCondition(final Condition<T> enableCondition) {
      myEnableCondition = enableCondition;
    }

    public JList getList() {
      return myList;
    }
  }

  public abstract class AddActionDescriptionBase<V> extends CustomActionDescription<V> {
    private final String myActionDescription;
    private final Factory<V> myAddHandler;
    private final boolean myCreateShortcut;
    private Icon myIcon = IconUtil.getAddIcon();

    public AddActionDescriptionBase(final String actionDescription, final Factory<V> addHandler, final boolean createShortcut) {
      myActionDescription = actionDescription;
      myAddHandler = addHandler;
      myCreateShortcut = createShortcut;
    }

    public BaseAction createAction(final JComponent component) {
      final ActionBehaviour<V> behaviour = new ActionBehaviour<V>() {
        public V performAction(final AnActionEvent e) {
          return addInternal(myAddHandler.create());
        }

        public void updateAction(final AnActionEvent e) {}
      };
      final BaseAction action = createAction(behaviour);
      if (myCreateShortcut) {
        action.registerCustomShortcutSet(CommonShortcuts.INSERT, component);
      }
      return action;
    }

    @Nullable
    protected abstract V addInternal(final V v);

    public Icon getActionIcon() {
      return myIcon;
    }

    public String getActionName() {
      return myActionDescription;
    }

    public void setIcon(final Icon icon) {
      myIcon = icon;
    }
  }

  public class AddActionDescription extends AddActionDescriptionBase<T> {
    public AddActionDescription(final String actionDescription, final Factory<T> addHandler, final boolean createShortcut) {
      super(actionDescription, addHandler, createShortcut);
    }

    protected T addInternal(final T t) {
      if (t != null) {
        handleNewElement(t);
      }
      return t;
    }
  }

  public class AddMultipleActionDescription extends AddActionDescriptionBase<Collection<T>> {
    public AddMultipleActionDescription(final String actionDescription, final Factory<Collection<T>> addHandler, final boolean createShortcut) {
      super(actionDescription, addHandler, createShortcut);
    }

    protected Collection<T> addInternal(final Collection<T> t) {
      if (t != null) {
        for (T element : t) {
          handleNewElement(element);
        }
      }
      return t;
    }
  }

  public class CopyActionDescription extends CustomActionDescription<T> {
    private final Convertor<T, T> myCopier;
    private final Condition<T> myEnabled;
    private final String myActionName;
    private boolean myVisibleWhenDisabled;

    public CopyActionDescription(final String actionName, final Convertor<T, T> copier, final Condition<T> enableCondition) {
      myActionName = actionName;
      myCopier = copier;
      myEnabled = enableCondition;
      myVisibleWhenDisabled = true;
    }

    public BaseAction createAction(final JComponent component) {
      final ActionBehaviour<T> behaviour = new ActionBehaviour<T>() {
        public T performAction(final AnActionEvent e) {
          final T newElement = myCopier.convert((T)myList.getSelectedValue());
          handleNewElement(newElement);
          return newElement;
        }

        public void updateAction(final AnActionEvent e) {
          final boolean applicable = myList.getSelectedIndices().length == 1;
          final Presentation presentation = e.getPresentation();
          if (!applicable) {
            presentation.setEnabled(applicable);
            return;
          }
          final boolean enabled = myEnabled.value((T)myList.getSelectedValue());
          presentation.setEnabled(enabled);
          presentation.setVisible(enabled || myVisibleWhenDisabled);
        }
      };
      return createAction(behaviour);
    }

    public Icon getActionIcon() {
      return PlatformIcons.COPY_ICON;
    }

    public String getActionName() {
      return myActionName;
    }

    public void setVisibleWhenDisabled(final boolean visible) {
      myVisibleWhenDisabled = visible;
    }
  }

  private static class FixedActionDescription extends ActionDescription {
    private final AnAction myAction;

    public FixedActionDescription(final AnAction action) {
      myAction = action;
    }

    public AnAction createAction(final JComponent component) {
      return myAction;
    }
  }

}
