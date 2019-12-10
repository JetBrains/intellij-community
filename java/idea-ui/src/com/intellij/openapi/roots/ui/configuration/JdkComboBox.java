// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel.NewSdkAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComboBoxPopupState;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkType;
import static com.intellij.openapi.roots.ui.configuration.JdkComboBox.JdkComboBoxItem;
import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

/**
 * @author Eugene Zhuravlev
 */
public class JdkComboBox extends ComboBox<JdkComboBoxItem> {
  private static final Logger LOG = Logger.getInstance(JdkComboBox.class);

  @NotNull private final Consumer<Sdk> myOnNewSdkAdded;
  @NotNull private final JdkListModelBuilder myModel;

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
    super();
    myOnNewSdkAdded = sdk -> {
      if (sdk == null) return;
      setSelectedJdk(sdk);
      if (onNewSdkAdded != null) {
        onNewSdkAdded.consume(sdk);
      }
    };

    UIUtil.putClientProperty(this, ANIMATION_IN_RENDERER_ALLOWED, true);

    setMinimumAndPreferredWidth(JBUI.scale(300));
    setMaximumRowCount(30);
    setSwingPopup(false);
    putClientProperty("ComboBox.jbPopup.supportUpdateModel", true);
    setRenderer(new JdkListPresenter(sdkModel) {
      @NotNull
      @Override
      protected JdkListModel getModel() {
        return ((JdkComboBoxModel)JdkComboBox.this.getModel()).myInnerModel;
      }

      @Override
      protected boolean showProgressIcon() {
        return JdkComboBox.this.isPopupVisible();
      }
    });

    myModel = new JdkListModelBuilder(project, sdkModel, sdkTypeFilter, creationFilter, sdkFilter) {
      @Override
      protected void syncModel(@NotNull JdkListModel model) {
        Object previousSelection = getSelectedItem();
        JdkComboBoxModel newModel = new JdkComboBoxModel(model);
        newModel.setSelectedItem(previousSelection);
        setModel(newModel);
      }
    };

    reloadModel();
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
        editButton.setEnabled(!(selectedItem instanceof InvalidJdkComboBoxItem) && selectedItem != null && selectedItem.getJdk() != null);
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

  public void setInvalidJdk(String name) {
    JdkComboBoxItem item = myModel.setInvalidJdk(name);
    setSelectedItem(item);
  }

  public void showProjectSdkItem() {
    myModel.setFirstItem(new ProjectJdkComboBoxItem());
  }

  public void showNoneSdkItem() {
    myModel.setFirstItem(new NoneJdkComboBoxItem());
  }

  public void reloadModel() {
    myModel.reloadSdks();
  }

  /**
   * @deprecated use {@link #reloadModel()}, you may also need to call
   * {@link #showNoneSdkItem()} or {@link #showProjectSdkItem()} once
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void reloadModel(JdkComboBoxItem firstItem, @Nullable Project project) {
    if (firstItem != null) {
      myModel.setFirstItem(firstItem);
    }
    reloadModel();
  }

  @Override
  public void firePopupMenuWillBecomeVisible() {
    resolveSuggestionsIfNeeded();
    super.firePopupMenuWillBecomeVisible();
  }

  private void resolveSuggestionsIfNeeded() {
    myModel.reloadActions(this, getSelectedJdk(), myOnNewSdkAdded);
    myModel.detectItems(this, myOnNewSdkAdded);
  }

  @Override
  public void setSelectedItem(@Nullable Object anObject) {
    if (anObject == null) {
      JdkComboBoxItem item = myModel.setFirstItem(new ProjectJdkComboBoxItem());
      setSelectedItem(item);
      return;
    }

    if (anObject instanceof Sdk) {
      // it is a chance we have a cloned SDK instance from the model here, or an original one
      // reload model is needed to make sure we see all instances
      myModel.reloadSdks();
      ((JdkComboBoxModel)getModel()).trySelectSdk((Sdk)anObject);
      return;
    }

    if (anObject instanceof ActionJdkItem) {
      ((ActionJdkItem)anObject).executeAction();
      return;
    }

    if (anObject instanceof SuggestedJdkItem) {
      ((SuggestedJdkItem)anObject).executeAction();
      return;
    }

    if (!(anObject instanceof JdkComboBoxItem)) return;
    super.setSelectedItem(anObject);
  }

  private static class JdkComboBoxModel extends AbstractListModel<JdkComboBoxItem>
                                        implements ComboBoxPopupState<JdkComboBoxItem>, ComboBoxModel<JdkComboBoxItem> {
    private final JdkListModel myInnerModel;
    private JdkComboBoxItem mySelectedItem;

    JdkComboBoxModel(@NotNull JdkListModel innerModel) {
      myInnerModel = innerModel;
    }

    @Override
    public int getSize() {
      return myInnerModel.getItems().size();
    }

    @Override
    public JdkComboBoxItem getElementAt(int index) {
      return myInnerModel.getItems().get(index);
    }

    @Nullable
    @Override
    public ListModel<JdkComboBoxItem> onChosen(JdkComboBoxItem selectedValue) {
      if (!(selectedValue instanceof ActionGroupJdkItem)) {
        return null;
      }

      ActionGroupJdkItem group = (ActionGroupJdkItem)selectedValue;
      return new JdkComboBoxModel(myInnerModel.buildSubModel(group));
    }

    @Override
    public boolean hasSubstep(JdkComboBoxItem selectedValue) {
      return selectedValue instanceof ActionGroupJdkItem;
    }

    @Override
    public void setSelectedItem(Object anObject) {
      if (!(anObject instanceof JdkComboBoxItem)) return;

      if (!myInnerModel.getItems().contains(anObject)) return;
      mySelectedItem = (JdkComboBoxItem)anObject;
      fireContentsChanged(this, -1, -1);
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    void trySelectSdk(@NotNull Sdk sdk) {
      ActualJdkComboBoxItem item = myInnerModel.findSdkItem(sdk);
      if (item == null) return;
      setSelectedItem(item);
    }
  }

  @NotNull
  public static Condition<Sdk> getSdkFilter(@Nullable final Condition<? super SdkTypeId> filter) {
    return filter == null ? Conditions.alwaysTrue() : sdk -> filter.value(sdk.getSdkType());
  }

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

  public static class ActualJdkComboBoxItem extends JdkComboBoxItem {
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

    /*abstract*/ boolean hasSameSdk(@NotNull Sdk value) {
      return Objects.equals(myJdk, value);
    }
  }

  public static class ProjectJdkComboBoxItem extends JdkComboBoxItem {
    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ProjectJdkComboBoxItem;
    }
  }

  public static class NoneJdkComboBoxItem extends JdkComboBoxItem {
    public String toString() {
      return ProjectBundle.message("jdk.combo.box.none.item");
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

  static class InvalidJdkComboBoxItem extends JdkComboBoxItem {
    private final String mySdkName;

    InvalidJdkComboBoxItem(@NotNull String name) {
      mySdkName = name;
    }

    @NotNull
    @Override
    public String getSdkName() {
      return mySdkName;
    }
  }

  /**
   * Note, this type is never visible from the {@link #getSelectedItem()} method,
   * it is kept here for binary compatibility
   */
  public static class SuggestedJdkItem extends JdkComboBoxItem {
    private final SdkType mySdkType;
    private final String myPath;
    private final String myVersion;

    SuggestedJdkItem(@NotNull SdkType sdkType, @Nullable String version, @NotNull String path) {
      mySdkType = sdkType;
      myPath = path;
      myVersion = version;
    }

    @NotNull
    public SdkType getSdkType() {
      return mySdkType;
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @Nullable
    public String getVersion() {
      return myVersion;
    }

    @Override
    public String toString() {
      return myPath;
    }

    /*abstract*/ void executeAction() { }
  }

  enum ActionRole {
    DOWNLOAD, ADD
  }

  static abstract class ActionJdkItem extends JdkComboBoxItem {
    @Nullable final ActionGroupJdkItem myGroup;
    final ActionRole myRole;
    final NewSdkAction myAction;

    ActionJdkItem(@NotNull ActionRole role, @NotNull NewSdkAction action) {
      this(role, action, null);
    }

    ActionJdkItem(@NotNull ActionRole role, @NotNull NewSdkAction action, @Nullable ActionGroupJdkItem group) {
      myRole = role;
      myAction = action;
      myGroup = group;
    }

    @NotNull
    ActionJdkItem withGroup(@NotNull ActionGroupJdkItem group) {
      ActionJdkItem that = this;
      return new ActionJdkItem(myRole, myAction, group) {
        @Override
        void executeAction() {
          that.executeAction();
        }
      };
    }

    abstract void executeAction();
  }

  static final class ActionGroupJdkItem extends JdkComboBoxItem {
    final Icon myIcon;
    final String myCaption;
    final List<? extends JdkComboBoxItem> mySubItems;

    ActionGroupJdkItem(@NotNull Icon icon,
                       @NotNull String caption,
                       @NotNull List<ActionJdkItem> subItems) {
      myIcon = icon;
      myCaption = caption;
      mySubItems = ImmutableList.copyOf(ContainerUtil.map(subItems, it -> it.withGroup(this)));
    }
  }

  /**
   * @deprecated Use the {@link JdkComboBox} API to manage shown items,
   * this call is ignored
   */
  @Override
  @Deprecated
  public void addItem(JdkComboBoxItem item) {
    LOG.warn("JdkComboBox#addItem() is deprecated!" + item, new RuntimeException());
  }

  /**
   * @deprecated Use the {@link JdkComboBox} API to manage shown items,
   * this call is ignored
   */
  @Override
  @Deprecated
  public void insertItemAt(JdkComboBoxItem item, int index) {
    LOG.warn("JdkComboBox#insertItemAt() is deprecated!" + item + " at " + index, new RuntimeException());
    if (item instanceof ProjectJdkComboBoxItem) {
      this.showProjectSdkItem();
    }
  }

  /**
   * @deprecated Use the {@link JdkComboBox} API to manage shown items,
   * this call is ignored
   */
  @Override
  @Deprecated
  public void removeItem(Object anObject) {
    LOG.warn("JdkComboBox#removeItem() is deprecated!", new RuntimeException());
  }

  /**
   * @deprecated Use the {@link JdkComboBox} API to manage shown items,
   * this call is ignored
   */
  @Override
  @Deprecated
  public void removeItemAt(int anIndex) {
    LOG.warn("JdkComboBox#removeItemAt() is deprecated!", new RuntimeException());
  }

  /**
   * @deprecated Use the {@link JdkComboBox} API to manage shown items,
   * this call is ignored
   */
  @Override
  @Deprecated
  public void removeAllItems() {
    LOG.warn("JdkComboBox#removeAllItems() is deprecated!", new RuntimeException());
  }
}
