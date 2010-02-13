/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.*;
import com.intellij.util.Icons;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class NamedItemsListEditor<T> extends MasterDetailsComponent {
    private final Namer<T> myNamer;
    private final Factory<T> myFactory;
    private final Cloner<T> myCloner;
    private final List<T> myItems = new ArrayList<T>();
    private final Equality<T> myComparer;
    private List<T> myResultItems;
    private final List<T> myOriginalItems;

    protected NamedItemsListEditor(Namer<T> namer,
                                   Factory<T> factory,
                                   Cloner<T> cloner,
                                   Equality<T> comparer,
                                   List<T> items) {
        myNamer = namer;
        myFactory = factory;
        myCloner = cloner;
        myComparer = comparer;

        myOriginalItems = items;
        myResultItems = items;
        reset();

        initTree();
    }

    public void reset() {
        myResultItems = myOriginalItems;
        myItems.clear();

        myRoot.removeAllChildren();
        for (T item : myOriginalItems) {
            addNewNode(myCloner.cloneOf(item));
        }

        super.reset();
    }

    protected void processRemovedItems() {
    }

    protected boolean wasObjectStored(Object editableObject) {
        return true;
    }

    protected String subjDisplayName() {
        return "item";
    }

    @Nullable
    public String askForProfileName(String titlePattern) {
        String title = MessageFormat.format(titlePattern, subjDisplayName());
        return Messages.showInputDialog("New " + subjDisplayName() + " name:", title, Messages.getQuestionIcon(), "", new InputValidator() {
            public boolean checkInput(String s) {
                return s.length() > 0 && findByName(s) == null;
            }

            public boolean canClose(String s) {
                return checkInput(s);
            }
        });
    }

    @Nullable
    private T findByName(String name) {
        for (T item : myItems) {
            if (Comparing.equal(name, myNamer.getName(item))) return item;
        }

        return null;
    }

    @Nullable
    protected ArrayList<AnAction> createActions(boolean fromPopup) {
        ArrayList<AnAction> result = new ArrayList<AnAction>();
        result.add(new AddAction());

        result.add(new MyDeleteAction(new Condition<Object>() {
            @SuppressWarnings({"unchecked"})
            public boolean value(Object o) {
                return canDelete((T) ((MyNode) o).getConfigurable().getEditableObject());
            }
        }));

        result.add(new CopyAction());

        return result;
    }

    private void addNewNode(T item) {
        addNode(new MyNode(new ItemConfigurable(item)), myRoot);
        myItems.add(item);
    }

    protected boolean canDelete(T item) {
        return true;
    }

    protected abstract UnnamedConfigurable createConfigurable(T item);

    @Override
    protected void onItemDeleted(Object item) {
        myItems.remove((T)item);
    }

    protected void setDisplayName(T item, String name) {
      myNamer.setName(item, name);
    }

    @Nullable
    protected UnnamedConfigurable getItemConfigurable(final T item) {
      final Ref<UnnamedConfigurable> result = new Ref<UnnamedConfigurable>();
      TreeUtil.traverse((TreeNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
        public boolean accept(Object node) {
          final NamedConfigurable configurable = (NamedConfigurable)((DefaultMutableTreeNode)node).getUserObject();
          if (configurable.getEditableObject() == item) {
            result.set(((ItemConfigurable)configurable).myConfigurable);
            return false;
          }
          else {
            return true;
          }
        }
      });
      return result.get();
    }

  private class ItemConfigurable extends NamedConfigurable {
        private final T myItem;
        private final UnnamedConfigurable myConfigurable;

        public ItemConfigurable(T item) {
            super(myNamer.canRename(item), TREE_UPDATER);
            myItem = item;
            myConfigurable = createConfigurable(item);
        }

        public void setDisplayName(String name) {
          NamedItemsListEditor.this.setDisplayName(myItem, name);
        }

        public Object getEditableObject() {
            return myItem;
        }

        public String getBannerSlogan() {
            return myNamer.getName(myItem);
        }

        public JComponent createOptionsPanel() {
            return myConfigurable.createComponent();
        }

        public String getDisplayName() {
            return myNamer.getName(myItem);
        }

        public Icon getIcon() {
            if (myConfigurable instanceof Iconable) {
              return ((Iconable)myConfigurable).getIcon(0);
            }
            return null;
        }

        public String getHelpTopic() {
            return null;
        }

        public boolean isModified() {
            return myConfigurable.isModified();
        }

        public void apply() throws ConfigurationException {
            myConfigurable.apply();
        }

        public void reset() {
            myConfigurable.reset();
        }

        public void disposeUIResources() {
            myConfigurable.disposeUIResources();
        }
    }

    @Override
    public boolean isModified() {
        if (myResultItems.size() != myItems.size()) return true;

        for (int i = 0; i < myItems.size(); i++) {
            if (!myComparer.equals(myItems.get(i), myResultItems.get(i))) return true;
        }

        return super.isModified();
    }

    public void apply() throws ConfigurationException {
        super.apply();
        myResultItems = myItems;
    }

    protected List<T> getCurrentItems() {
      return Collections.unmodifiableList(myItems);
    }

    public List<T> getItems() {
        return myResultItems;
    }

    public T getSelectedItem() {
        return (T) getSelectedObject();
    }


    private class CopyAction extends DumbAwareAction {
        public CopyAction() {
            super("Copy", "Copy", MasterDetailsComponent.COPY_ICON);
            registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_MASK)), myTree);
        }

        public void actionPerformed(AnActionEvent event) {
            final String profileName = askForProfileName("Copy {0}");
            if (profileName == null) return;

            final T clone = myCloner.copyOf((T) getSelectedObject());
            myNamer.setName(clone, profileName);
            addNewNode(clone);
            selectNodeInTree(clone);
            onItemCloned(clone);
        }


        public void update(AnActionEvent event) {
            super.update(event);
            event.getPresentation().setEnabled(getSelectedObject() != null);
        }
    }

    protected void onItemCloned(T clone) {
    }

  private class AddAction extends DumbAwareAction {
        public AddAction() {
            super("Add", "Add", Icons.ADD_ICON);
            registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
        }

        public void actionPerformed(AnActionEvent event) {
          final T newItem = createItem();
          if (newItem != null) {
            onItemCreated(newItem);
          }
        }
    }

    public void selectItem(T item) {
        selectNodeInTree(findByName(myNamer.getName(item)));
    }

    @Nullable
    protected T createItem() {
      final String name = askForProfileName("Create new {0}");
      if (name == null) return null;
      final T newItem = myFactory.create();
      myNamer.setName(newItem, name);
      return newItem;
    }

    protected void onItemCreated(T newItem) {
      addNewNode(newItem);
      selectNodeInTree(newItem);
    }

}
