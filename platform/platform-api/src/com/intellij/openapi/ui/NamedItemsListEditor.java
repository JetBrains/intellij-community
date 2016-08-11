/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.util.IconUtil;
import com.intellij.util.ui.tree.TreeUtil;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class NamedItemsListEditor<T> extends MasterDetailsComponent {
    private final Namer<T> myNamer;
    private final Factory<T> myFactory;
    private final Cloner<T> myCloner;
    private final List<T> myItems = new ArrayList<>();
    private final Equality<T> myComparer;
    private List<T> myResultItems;
    private final List<T> myOriginalItems;
    private boolean myShowIcons;

    protected NamedItemsListEditor(Namer<T> namer,
                                   Factory<T> factory,
                                   Cloner<T> cloner,
                                   Equality<T> comparer,
                                   List<T> items) {
      this(namer, factory, cloner, comparer, items, true);
    }

    protected NamedItemsListEditor(Namer<T> namer,
                                   Factory<T> factory,
                                   Cloner<T> cloner,
                                   Equality<T> comparer,
                                   List<T> items,
                                   boolean initInConstructor) {
        myNamer = namer;
        myFactory = factory;
        myCloner = cloner;
        myComparer = comparer;

        myOriginalItems = items;
        myResultItems = items;
        if (initInConstructor) {
          reset();
          initTree();
        }
    }

    @Override
    public void reset() {
        myResultItems = myOriginalItems;
        myItems.clear();

        clearChildren();
        for (T item : myOriginalItems) {
            addNewNode(myCloner.cloneOf(item));
        }

        super.reset();
    }

    @Override
    protected void processRemovedItems() {
    }

    @Override
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
            @Override
            public boolean checkInput(String s) {
                return s.length() > 0 && findByName(s) == null;
            }

            @Override
            public boolean canClose(String s) {
                return checkInput(s);
            }
        });
    }

    @Nullable
    protected T findByName(String name) {
        for (T item : myItems) {
            if (Comparing.equal(name, myNamer.getName(item))) return item;
        }

        return null;
    }

    @Override
    @Nullable
    protected ArrayList<AnAction> createActions(boolean fromPopup) {
        ArrayList<AnAction> result = new ArrayList<>();
        result.add(new AddAction());

        result.add(new MyDeleteAction(forAll(o -> canDelete((T) ((MyNode) o).getConfigurable().getEditableObject()))));

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
      //noinspection unchecked
      myItems.remove((T)item);
    }

    protected void setDisplayName(T item, String name) {
      myNamer.setName(item, name);
    }

    public void setShowIcons(boolean showIcons) {
      myShowIcons = showIcons;
    }

    @Nullable
    protected UnnamedConfigurable getItemConfigurable(final T item) {
      final Ref<UnnamedConfigurable> result = new Ref<>();
      TreeUtil.traverse((TreeNode)myTree.getModel().getRoot(), new TreeUtil.Traverse() {
        @Override
        public boolean accept(Object node) {
          final NamedConfigurable configurable = (NamedConfigurable)((DefaultMutableTreeNode)node).getUserObject();
          if (configurable.getEditableObject() == item) {
            //noinspection unchecked
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

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();    //To change body of overridden methods use File | Settings | File Templates.
  }

  private class ItemConfigurable extends NamedConfigurable {
        private final T myItem;
        private final UnnamedConfigurable myConfigurable;

        public ItemConfigurable(T item) {
            super(myNamer.canRename(item), TREE_UPDATER);
            myItem = item;
            myConfigurable = createConfigurable(item);
        }

        @Override
        public void setDisplayName(String name) {
          NamedItemsListEditor.this.setDisplayName(myItem, name);
        }

        @Override
        public Object getEditableObject() {
            return myItem;
        }

        @Override
        public String getBannerSlogan() {
            return myNamer.getName(myItem);
        }

        @Override
        public JComponent createOptionsPanel() {
            return myConfigurable.createComponent();
        }

        @Override
        public String getDisplayName() {
            return myNamer.getName(myItem);
        }

        @Override
        public Icon getIcon(boolean expanded) {
            if (myShowIcons && myConfigurable instanceof Iconable) {
              return ((Iconable)myConfigurable).getIcon(0);
            }
            return null;
        }

    @Override
    public String getHelpTopic() {
            return null;
        }

        @Override
        public boolean isModified() {
            return myConfigurable.isModified();
        }

        @Override
        public void apply() throws ConfigurationException {
            myConfigurable.apply();
        }

        @Override
        public void reset() {
            myConfigurable.reset();
        }

        @Override
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

    @Override
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
      //noinspection unchecked
      return (T) getSelectedObject();
    }


    private class CopyAction extends DumbAwareAction {
        public CopyAction() {
            super("Copy", "Copy", MasterDetailsComponent.COPY_ICON);
            registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)), myTree);
        }

        @Override
        public void actionPerformed(AnActionEvent event) {
            final String profileName = askForProfileName("Copy {0}");
            if (profileName == null) return;

            @SuppressWarnings("unchecked") final T clone = myCloner.copyOf((T) getSelectedObject());
            myNamer.setName(clone, profileName);
            addNewNode(clone);
            selectNodeInTree(clone);
            onItemCloned(clone);
        }


        @Override
        public void update(AnActionEvent event) {
            super.update(event);
            event.getPresentation().setEnabled(getSelectedObject() != null);
        }
    }

    protected void onItemCloned(T clone) {
    }

  private class AddAction extends DumbAwareAction {
        public AddAction() {
            super("Add", "Add", IconUtil.getAddIcon());
            registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
        }

        @Override
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
