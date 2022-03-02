// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.ide.IdeBundle;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public abstract class NamedItemsListEditor<T> extends MasterDetailsComponent {
  private final Namer<? super T> myNamer;
  private final Factory<? extends T> myFactory;
  private final Cloner<T> myCloner;
  private final List<T> myItems = new ArrayList<>();
  private final BiPredicate<? super T, ? super T> myComparer;
  private List<T> myResultItems;
  private final List<T> myOriginalItems;
  private boolean myShowIcons;

  protected NamedItemsListEditor(Namer<? super T> namer,
                                 Factory<? extends T> factory,
                                 Cloner<T> cloner,
                                 BiPredicate<? super T, ? super T> comparer,
                                 List<T> items) {
    this(namer, factory, cloner, comparer, items, true);
  }

  protected NamedItemsListEditor(Namer<? super T> namer,
                                 Factory<? extends T> factory,
                                 Cloner<T> cloner,
                                 BiPredicate<? super T, ? super T> comparer,
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
  protected boolean wasObjectStored(Object editableObject) {
    return true;
  }

  /**
   * @deprecated override {@link #getCopyDialogTitle()}, {@link #getCreateNewDialogTitle()}, {@link #getNewLabelText()} instead
   */
  @SuppressWarnings({"DeprecatedIsStillUsed", "HardCodedStringLiteral"})
  @Deprecated(forRemoval = true)
  protected String subjDisplayName() {
    return "item";
  }

  /**
   * Returns title for "Copy" dialog. The method must be overriden in the implementations because the default implementation isn't friendly
   * for localization.
   */
  protected @NlsContexts.DialogTitle String getCopyDialogTitle() {
    //noinspection HardCodedStringLiteral
    return "Copy " + subjDisplayName();
  }

  /**
   * Returns label text for "Copy" and "Create New" dialogs. The method must be overriden in the implementations because the default
   * implementation isn't friendly for localization.
   */
  protected @NlsContexts.Label String getNewLabelText() {
    //noinspection HardCodedStringLiteral
    return "New " + subjDisplayName() + " name:";
  }

  /**
   * Returns title for "Create New" dialog. The method must be overriden in the implementations because the default implementation isn't friendly
   * for localization.
   */
  protected @NlsContexts.DialogTitle String getCreateNewDialogTitle() {
    //noinspection HardCodedStringLiteral
    return "Create New " + subjDisplayName();
  }


  @Nullable
  public String askForProfileName(@NlsContexts.DialogTitle String title) {
    return Messages.showInputDialog(getNewLabelText(), title, Messages.getQuestionIcon(), "", new InputValidator() {
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
  protected T findByName(@NlsSafe String name) {
    for (T item : myItems) {
      if (Objects.equals(name, myNamer.getName(item))) return item;
    }

    return null;
  }

  @Override
  @Nullable
  protected List<AnAction> createActions(boolean fromPopup) {
    ArrayList<AnAction> result = new ArrayList<>();
    result.add(new AddAction());
    //noinspection unchecked
    result.add(new MyDeleteAction(forAll(o -> canDelete((T)((MyNode)o).getConfigurable().getEditableObject()))));
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
    TreeUtil.traverse((TreeNode)myTree.getModel().getRoot(), node -> {
      final NamedConfigurable<?> configurable = (NamedConfigurable<?>)((DefaultMutableTreeNode)node).getUserObject();
      if (configurable.getEditableObject() == item) {
        //noinspection unchecked
        result.set(((ItemConfigurable)configurable).myConfigurable);
        return false;
      }
      else {
        return true;
      }
    });
    return result.get();
  }

  private class ItemConfigurable extends NamedConfigurable {
    private final T myItem;
    private final UnnamedConfigurable myConfigurable;

    ItemConfigurable(T item) {
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
      if (!myComparer.test(myItems.get(i), myResultItems.get(i))) {
        return true;
      }
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
    return (T)getSelectedObject();
  }


  private class CopyAction extends DumbAwareAction {
    CopyAction() {
      super(IdeBundle.messagePointer("action.NamedItemsListEditor.CopyAction.text.copy"),
            IdeBundle.messagePointer("action.NamedItemsListEditor.CopyAction.description.copy"), MasterDetailsComponent.COPY_ICON);
      registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK)), myTree);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      String profileName = askForProfileName(getCopyDialogTitle());
      if (profileName == null) return;

      @SuppressWarnings("unchecked") final T clone = myCloner.copyOf((T)getSelectedObject());
      myNamer.setName(clone, profileName);
      addNewNode(clone);
      selectNodeInTree(clone);
      onItemCloned(clone);
    }


    @Override
    public void update(@NotNull AnActionEvent event) {
      super.update(event);
      event.getPresentation().setEnabled(getSelectedObject() != null);
    }
  }

  protected void onItemCloned(T clone) {
  }

  private final class AddAction extends DumbAwareAction {
    AddAction() {
      super(IdeBundle.messagePointer("action.NamedItemsListEditor.AddAction.text.add"),
            IdeBundle.messagePointer("action.NamedItemsListEditor.AddAction.description.add"), IconUtil.getAddIcon());
      registerCustomShortcutSet(CommonShortcuts.INSERT, myTree);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
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
    String name = askForProfileName(getCreateNewDialogTitle());
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
