// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ui.configuration.SdkListItem.SdkItem;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBoxPopupState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.openapi.roots.ui.configuration.JdkComboBox.JdkComboBoxItem;

/**
 * @author Eugene Zhuravlev
 */
public class JdkComboBox extends SdkComboBoxBase<JdkComboBoxItem> {
  private static final Logger LOG = Logger.getInstance(JdkComboBox.class);
  @NotNull private final Consumer<Sdk> myOnNewSdkAdded;

  @Nullable private JButton mySetUpButton;

  /**
   * @deprecated since {@link #setSetupButton} methods are deprecated, use the
   * more specific constructor to pass all parameters
   */
  @Deprecated
  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel) {
    this(jdkModel, null);
  }

  /**
   * @deprecated since {@link #setSetupButton} methods are deprecated, use the
   * more specific constructor to pass all parameters
   */
  @Deprecated
  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<? super SdkTypeId> filter) {
    this(jdkModel, filter, getSdkFilter(filter), filter, false);
  }

  /**
   * @deprecated since {@link #setSetupButton} methods are deprecated, use the
   * more specific constructor to pass all parameters
   *
   * The {@param addSuggestedItems} is ignored (it was not actively used) and
   * it is no longer possible to have {@link SuggestedJdkItem} as a selected
   * item of that ComboBox. The implementation will take care about turning a
   * suggested SDKs into {@link Sdk}s
   */
  @Deprecated
  @SuppressWarnings("unused")
  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                     @Nullable Condition<? super Sdk> filter,
                     @Nullable Condition<? super SdkTypeId> creationFilter,
                     boolean addSuggestedItems) {
    this(null, jdkModel, sdkTypeFilter, filter, creationFilter, null);
  }

  /**
   * Creates new Sdk selector combobox
   * @param project current project (if any)
   * @param sdkModel the sdks model
   * @param sdkTypeFilter sdk types filter predicate to show
   * @param sdkFilter filters Sdk instances that are listed, it implicitly includes the {@param sdkTypeFilter}
   * @param creationFilter a filter of SdkType that allowed to create a new Sdk with that control
   * @param onNewSdkAdded a callback that is executed once a new Sdk is added to the list
   */
  public JdkComboBox(@Nullable Project project,
                     @NotNull ProjectSdksModel sdkModel,
                     @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                     @Nullable Condition<? super Sdk> sdkFilter,
                     @Nullable Condition<? super SdkTypeId> creationFilter,
                     @Nullable Consumer<? super Sdk> onNewSdkAdded) {
    super(new SdkListModelBuilder(project, sdkModel, sdkTypeFilter, SimpleJavaSdkType.notSimpleJavaSdkType(creationFilter), sdkFilter));
    myOnNewSdkAdded = sdk -> {
      if (onNewSdkAdded != null) {
        onNewSdkAdded.consume(sdk);
      }
    };
    setRenderer(new SdkListPresenter(() -> ((JdkComboBoxModel)this.getModel()).myInnerModel).forType(JdkComboBox::unwrapItem));
    reloadModel();
  }

  @Override
  protected void onModelUpdated(@NotNull SdkListModel model) {
    Object previousSelection = getSelectedItem();
    JdkComboBoxModel newModel = new JdkComboBoxModel(model);
    newModel.setSelectedItem(previousSelection);
    setModel(newModel);
  }

  @NotNull
  private static SdkListItem unwrapItem(@Nullable JdkComboBoxItem item) {
    if (item == null) item = new ProjectJdkComboBoxItem();

    if (item instanceof InnerComboBoxItem) {
      return ((InnerComboBoxItem)item).getItem();
    }
    throw new RuntimeException("Failed to unwrap " + item.getClass().getName() + ": " + item);
  }

  @NotNull
  private static JdkComboBoxItem wrapItem(@NotNull SdkListItem item) {
    if (item instanceof SdkListItem.SdkItem) {
      return new ActualJdkInnerItem((SdkListItem.SdkItem)item);
    }

    if (item instanceof SdkListItem.NoneSdkItem) {
      return new NoneJdkComboBoxItem();
    }

    if (item instanceof SdkListItem.ProjectSdkItem) {
      return new ProjectJdkComboBoxItem();
    }

    return new InnerJdkComboBoxItem(item);
  }

  /**
   * @deprecated Use the overloaded constructor to pass these parameters directly to
   * that class. The {@param setUpButton} is no longer used, the JdkComboBox shows
   * all the needed actions in the popup. The button will be made invisible.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void setSetupButton(final JButton setUpButton,
                                @Nullable final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<? super Sdk> additionalSetup,
                                final boolean moduleJdkSetup) {
    setSetupButton(setUpButton, project, jdksModel, firstItem, additionalSetup,"");
  }

  /**
   * @deprecated Use the overloaded constructor to pass these parameters directly to
   * that class. The {@param setUpButton} is no longer used, the JdkComboBox shows
   * all the needed actions in the popup. The button will be made invisible.
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void setSetupButton(final JButton setUpButton,
                                @Nullable final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<? super Sdk> additionalSetup,
                                final String actionGroupTitle) {

    mySetUpButton = setUpButton;
    mySetUpButton.setVisible(false);
  }

  public void setEditButton(@NotNull JButton editButton,
                            @NotNull Project project,
                            @NotNull Supplier<? extends Sdk> retrieveJDK) {
    editButton.addActionListener(e -> {
      final Sdk projectJdk = retrieveJDK.get();
      if (projectJdk != null) {
        ProjectStructureConfigurable.getInstance(project).select(projectJdk, true);
      }
    });
    addActionListener(e -> {
      final JdkComboBoxItem selectedItem = getSelectedItem();
      if (selectedItem instanceof ProjectJdkComboBoxItem) {
        editButton.setEnabled(ProjectStructureConfigurable.getInstance(project).getProjectJdksModel().getProjectSdk() != null);
      }
      else {
        editButton.setEnabled(selectedItem != null && selectedItem.getJdk() != null);
      }
    });
  }

  /**
   *
   * @deprecated the popup shown by the SetUp button is now included
   * directly into the popup, you may remove the button from your UI,
   * see {@link #setSetupButton(JButton, Project, ProjectSdksModel, JdkComboBoxItem, Condition, boolean)}
   * for more details
   */
  @Nullable
  @Deprecated
  public JButton getSetUpButton() {
    return mySetUpButton;
  }

  @Nullable
  @Override
  public JdkComboBoxItem getSelectedItem() {
    return (JdkComboBoxItem)super.getSelectedItem();
  }

  @Nullable
  public Sdk getSelectedJdk() {
    final JdkComboBoxItem selectedItem = getSelectedItem();
    return selectedItem != null? selectedItem.getJdk() : null;
  }

  public void setSelectedJdk(@Nullable Sdk jdk) {
    setSelectedItem(jdk);
  }

  /**
   * @deprecated use {@link #reloadModel()}, you may also need to call
   * {@link #showNoneSdkItem()} or {@link #showProjectSdkItem()} once
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void reloadModel(JdkComboBoxItem firstItem, @Nullable Project project) {
    processFirstItem(firstItem);
    reloadModel();
  }

  private void processFirstItem(@Nullable JdkComboBoxItem firstItem) {
    if (firstItem instanceof ProjectJdkComboBoxItem) {
      myModel.showProjectSdkItem();
    } else if (firstItem instanceof NoneJdkComboBoxItem) {
      myModel.showNoneSdkItem();
    } else if (firstItem instanceof ActualJdkComboBoxItem) {
      setSelectedJdk(((ActualJdkComboBoxItem)firstItem).myJdk);
    }
  }

  @Override
  public void firePopupMenuWillBecomeVisible() {
    resolveSuggestionsIfNeeded();
    super.firePopupMenuWillBecomeVisible();
  }

  private void resolveSuggestionsIfNeeded() {
    myModel.reloadActions();

    DialogWrapper dialogWrapper = DialogWrapper.findInstance(this);
    if (dialogWrapper == null) {
      LOG.warn("Cannot find DialogWrapper parent for the JdkComboBox " + this + ", SDK search is disabled", new RuntimeException());
      return;
    }

    myModel.detectItems(this, dialogWrapper.getDisposable());
  }

  @Override
  public void setSelectedItem(@Nullable Object anObject) {
    if (anObject instanceof SdkListItem) {
      setSelectedItem(wrapItem((SdkListItem)anObject));
      return;
    }

    if (anObject == null) {
      SdkListModel innerModel = ((JdkComboBoxModel)getModel()).myInnerModel;
      SdkListItem candidate = innerModel.findProjectSdkItem();
      if (candidate == null) {
        candidate = innerModel.findNoneSdkItem();
      }
      if (candidate == null) {
        candidate = myModel.showProjectSdkItem();
      }

      setSelectedItem(candidate);
      return;
    }

    if (anObject instanceof Sdk) {
      // it is a chance we have a cloned SDK instance from the model here, or an original one
      // reload model is needed to make sure we see all instances
      myModel.reloadSdks();
      ((JdkComboBoxModel)getModel()).trySelectSdk((Sdk)anObject);
      return;
    }

    if (anObject instanceof InnerComboBoxItem) {
      SdkListItem item = ((InnerComboBoxItem)anObject).getItem();
      if (myModel.executeAction(this, item, newItem -> {
        setSelectedItem(newItem);
        if (newItem instanceof SdkItem) {
          myOnNewSdkAdded.consume(((SdkItem)newItem).getSdk());
        }
      })) return;
    }

    if (anObject instanceof SelectableComboBoxItem) {
      super.setSelectedItem(anObject);
    }
  }

  private static class JdkComboBoxModel extends AbstractListModel<JdkComboBoxItem>
                                        implements ComboBoxPopupState<JdkComboBoxItem>, ComboBoxModel<JdkComboBoxItem> {
    private final SdkListModel myInnerModel;
    private JdkComboBoxItem mySelectedItem;

    JdkComboBoxModel(@NotNull SdkListModel innerModel) {
      myInnerModel = innerModel;
    }

    @Override
    public int getSize() {
      return myInnerModel.getItems().size();
    }

    @Override
    public JdkComboBoxItem getElementAt(int index) {
      return wrapItem(myInnerModel.getItems().get(index));
    }

    @Nullable
    @Override
    public ListModel<JdkComboBoxItem> onChosen(JdkComboBoxItem selectedValue) {
      if (selectedValue instanceof InnerComboBoxItem) {
        SdkListModel inner = myInnerModel.onChosen(((InnerComboBoxItem)selectedValue).getItem());
        return inner == null ? null : new JdkComboBoxModel(inner);
      }
      return null;
    }

    @Override
    public boolean hasSubstep(JdkComboBoxItem selectedValue) {
      if (selectedValue instanceof InnerComboBoxItem) {
        return myInnerModel.hasSubstep(((InnerComboBoxItem)selectedValue).getItem());
      }
      return false;
    }

    @Override
    public void setSelectedItem(Object anObject) {
      if (!(anObject instanceof JdkComboBoxItem)) return;
      if (!(anObject instanceof InnerComboBoxItem)) return;
      SdkListItem innerItem = ((InnerComboBoxItem)anObject).getItem();
      if (!myInnerModel.getItems().contains(innerItem)) return;
      mySelectedItem = (JdkComboBoxItem)anObject;
      fireContentsChanged(this, -1, -1);
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    void trySelectSdk(@NotNull Sdk sdk) {
      SdkItem item = myInnerModel.findSdkItem(sdk);
      if (item == null) return;
      setSelectedItem(wrapItem(item));
    }
  }

  @NotNull
  public static Condition<Sdk> getSdkFilter(@Nullable final Condition<? super SdkTypeId> filter) {
    return filter == null ? Conditions.alwaysTrue() : sdk -> filter.value(sdk.getSdkType());
  }

  private interface InnerComboBoxItem {
    @NotNull SdkListItem getItem();
  }

  private interface SelectableComboBoxItem { }

  public abstract static class JdkComboBoxItem {
    @Nullable
    public Sdk getJdk() {
      return null;
    }

    @Nullable
    public String getSdkName() {
      return null;
    }
  }

  private static final class InnerJdkComboBoxItem extends JdkComboBoxItem implements InnerComboBoxItem {
    private final SdkListItem myItem;

    private InnerJdkComboBoxItem(@NotNull SdkListItem item) {
      myItem = item;
    }

    @NotNull
    @Override
    public SdkListItem getItem() {
      return myItem;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      InnerJdkComboBoxItem item = (InnerJdkComboBoxItem)o;
      return myItem.equals(item.myItem);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myItem);
    }
  }

  private static final class ActualJdkInnerItem extends ActualJdkComboBoxItem implements InnerComboBoxItem {
    private final SdkItem myItem;

    private ActualJdkInnerItem(@NotNull SdkItem item) {
      super(item.getSdk());
      myItem = item;
    }

    @NotNull
    @Override
    public SdkListItem getItem() {
      return myItem;
    }
  }

  public static class ActualJdkComboBoxItem extends JdkComboBoxItem implements SelectableComboBoxItem {
    private final Sdk myJdk;

    public ActualJdkComboBoxItem(@NotNull Sdk jdk) {
      myJdk = jdk;
    }

    @Override
    public String toString() {
      return myJdk.getName();
    }

    @NotNull
    @Override
    public Sdk getJdk() {
      return myJdk;
    }

    @Nullable
    @Override
    public String getSdkName() {
      return myJdk.getName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ActualJdkComboBoxItem item = (ActualJdkComboBoxItem)o;
      return myJdk.equals(item.myJdk);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myJdk);
    }
  }

  public static class ProjectJdkComboBoxItem extends JdkComboBoxItem implements InnerComboBoxItem, SelectableComboBoxItem {
    @NotNull
    @Override
    public SdkListItem getItem() {
      return new SdkListItem.ProjectSdkItem();
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ProjectJdkComboBoxItem;
    }
  }

  public static class NoneJdkComboBoxItem extends JdkComboBoxItem implements InnerComboBoxItem, SelectableComboBoxItem {
    @NotNull
    @Override
    public SdkListItem getItem() {
      return new SdkListItem.NoneSdkItem();
    }

    public String toString() {
      return JavaUiBundle.message("jdk.combo.box.none.item");
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NoneJdkComboBoxItem;
    }
  }

  /**
   * @deprecated this type is never visible from the {@link #getSelectedItem()} method,
   * it is kept here for binary compatibility
   */
  @Deprecated
  public static class SuggestedJdkItem extends JdkComboBoxItem {
    private final SdkType mySdkType;
    private final String myPath;

    SuggestedJdkItem(@NotNull SdkType sdkType, @NotNull String path) {
      mySdkType = sdkType;
      myPath = path;
    }

    @NotNull
    public SdkType getSdkType() {
      return mySdkType;
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @Override
    public String toString() {
      return myPath;
    }
  }

  /**
   * @deprecated Use the {@link JdkComboBox} API to manage shown items,
   * this call is ignored
   */
  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public void insertItemAt(JdkComboBoxItem item, int index) {
    super.insertItemAt(item, index);
    processFirstItem(item);
  }
}
