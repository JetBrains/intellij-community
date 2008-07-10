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
import com.intellij.openapi.util.Cloner;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Factory;
import com.intellij.util.Icons;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
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
    private String askForProfileName(String title) {
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

    private class ItemConfigurable extends NamedConfigurable {
        private final T myItem;
        private final UnnamedConfigurable myConfigurable;

        public ItemConfigurable(T item) {
            super(myNamer.canRename(item), TREE_UPDATER);
            myItem = item;
            myConfigurable = createConfigurable(item);
        }

        public void setDisplayName(String name) {
            myNamer.setName(myItem, name);
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

    public List<T> getItems() {
        return myResultItems;
    }

    public T getSelectedItem() {
        return (T) getSelectedObject();
    }


    private class CopyAction extends AnAction {
        public CopyAction() {
            super("Copy", "Copy", MasterDetailsComponent.COPY_ICON);
            registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_MASK)), myTree);
        }

        public void actionPerformed(AnActionEvent event) {
            final String profileName = askForProfileName("Copy " + subjDisplayName());
            if (profileName == null) return;

            final T clone = myCloner.copyOf((T) getSelectedObject());
            myNamer.setName(clone, profileName);
            addNewNode(clone);
            selectNodeInTree(clone);
        }


        public void update(AnActionEvent event) {
            super.update(event);
            event.getPresentation().setEnabled(getSelectedObject() != null);
        }
    }

    private class AddAction extends AnAction {
        public AddAction() {
            super("Add", "Add", Icons.ADD_ICON);
            registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
        }

        public void actionPerformed(AnActionEvent event) {
            final String name = askForProfileName("Create new " + subjDisplayName());
            if (name == null) return;
            final T newItem = myFactory.create();
            myNamer.setName(newItem, name);

            addNewNode(newItem);
            selectNodeInTree(newItem);
        }
    }

    public void selectItem(T item) {
        selectNodeInTree(findByName(myNamer.getName(item)));
    }
}