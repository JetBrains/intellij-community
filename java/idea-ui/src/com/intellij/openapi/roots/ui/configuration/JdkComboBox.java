// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableList;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkListConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Predicate;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkType;
import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

/**
 * @author Eugene Zhuravlev
 */
public class JdkComboBox extends ComboBox<JdkComboBox.JdkComboBoxItem> {
  private static final Logger LOG = Logger.getInstance(JdkComboBox.class);
  private static final Icon EMPTY_ICON = EmptyIcon.create(1, 16);

  @Nullable private final Project myProject;
  @NotNull private final Condition<Sdk> mySdkFilter;
  @NotNull private final Condition<SdkTypeId> myCreationFilter;
  @NotNull private final Condition<SdkTypeId> mySdkTypeFilter;
  @NotNull private final Consumer<Sdk> myOnNewSdkAdded;
  @NotNull private final JdkComboBoxModelBuilder myModel;
  @NotNull private final ProjectSdksModel mySdkModel;

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
   * @param creationFilter ac filter of SdkType that allowed to create a new Sdk with that control
   * @param onNewSdkAdded a callback that is executed once a new Sdk is added to the list
   */
  public JdkComboBox(@Nullable Project project,
                     @NotNull ProjectSdksModel sdkModel,
                     @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                     @Nullable Condition<? super Sdk> sdkFilter,
                     @Nullable Condition<? super SdkTypeId> creationFilter,
                     @Nullable Consumer<? super Sdk> onNewSdkAdded) {
    super();
    myProject = project;
    mySdkModel = sdkModel;
    mySdkTypeFilter = matchAllIfNull(sdkTypeFilter);
    mySdkFilter = sdk -> sdk != null && mySdkTypeFilter.value(sdk.getSdkType()) && (sdkFilter == null || sdkFilter.value(sdk));
    myCreationFilter = notSimpleJavaSdkType(creationFilter);
    myOnNewSdkAdded = emptyIfNull(onNewSdkAdded);

    setMinimumAndPreferredWidth(JBUI.scale(400));
    setMaximumRowCount(20);
    setRenderer(new ColoredListCellRenderer<JdkComboBoxItem>() {
      @Override
      public Component getListCellRendererComponent(JList<? extends JdkComboBoxItem> list,
                                                    JdkComboBoxItem value,
                                                    int index,
                                                    boolean selected,
                                                    boolean hasFocus) {

        //allow AnimationIcon for loader to show progress
        UIUtil.putClientProperty(list, ANIMATION_IN_RENDERER_ALLOWED, true);
        UIUtil.putClientProperty(JdkComboBox.this, ANIMATION_IN_RENDERER_ALLOWED, true);

        SimpleColoredComponent component = (SimpleColoredComponent)super.getListCellRendererComponent(list, value, index, selected, hasFocus);

        //handle the selected item to show in the ComboBox, not in the popup
        if (index == -1) {
          return component;
        }

        JPanel panel = new JPanel(new BorderLayout()) {
          @Override
          public void setBorder(Border border) {
            // we do not want to outer UI to add a border to that JPanel
            // see com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI.CustomComboPopup#customizeListRendererComponent
            component.setBorder(border);
          }
        };
        panel.add(component, BorderLayout.CENTER);
        String separatorTextAbove = ((JdkComboBoxModel)getModel()).getSeparatorTextAbove(value);
        if (separatorTextAbove != null) {
          SeparatorWithText separator = new SeparatorWithText();
          if (!separatorTextAbove.isEmpty()) {
            separator.setCaption(separatorTextAbove);
          }
          panel.add(separator, BorderLayout.NORTH);
        }
        return panel;
      }

      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends JdkComboBoxItem> list,
                                           JdkComboBoxItem value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {

        if (JdkComboBox.this.isEnabled()) {
          setIcon(EMPTY_ICON);    // to fix vertical size
          if (value instanceof InvalidJdkComboBoxItem) {
            final String str = value.toString();
            append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
          }
          else if (value instanceof LoadingJdkComboBoxItem) {
            setIcon(new AnimatedIcon.Default());
            append(ProjectBundle.message("jdk.combo.box.search.of.sdks") );
          }
          else if (value instanceof ProjectJdkComboBoxItem) {
            final Sdk jdk = sdkModel.getProjectSdk();
            if (jdk != null) {
              setIcon(((SdkType)jdk.getSdkType()).getIcon());
              append(ProjectBundle.message("project.roots.project.jdk.inherited"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              append(" (" + jdk.getName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            else {
              final String str = value.toString();
              append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
          }
          else if (value instanceof SuggestedJdkItem) {
            SdkType type = ((SuggestedJdkItem)value).getSdkType();
            String home = ((SuggestedJdkItem)value).getPath();
            setIcon(type.getIconForAddAction());
            String version = ((SuggestedJdkItem)value).getVersion();
            append(version == null ? type.getPresentableName() : version);
            append(" (" + StringUtil.shortenTextWithEllipsis(home, 50, 20) + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          else if (value instanceof ActionJdkItem) {
            Presentation presentation = ((ActionJdkItem)value).getAction().getTemplatePresentation();
            setIcon(presentation.getIcon());
            String text = presentation.getText();
            append(text != null ? text : "<null>");
          }
          else if (value != null) {
            OrderEntryAppearanceService.getInstance().forJdk(value.getJdk(), false, selected, true).customize(this);
          }
          else {
            customizeCellRenderer(list, new NoneJdkComboBoxItem(), index, selected, hasFocus);
          }
        }
      }
    });

    myModel = new JdkComboBoxModelBuilder();
    myModel.reload();
  }

  /**
   * @deprecated Use the overloaded constructor to pass these parameters directly to
   * that class. The {@param setUpButton} is no longer used, the JdkComboBox shows
   * all the needed actions in the popup. The button will be be made invisible.
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
   * all the needed actions in the popup. The button will be be made invisible.
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

  private void collectNewSdkActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    mySdkModel.createAddActions(group, this, getSelectedJdk(), jdk -> {
      if (myProject != null) {
        final JdkListConfigurable configurable = JdkListConfigurable.getInstance(myProject);
        configurable.addJdkNode(jdk, false);
      }
      reloadModel();
      setSelectedJdk(jdk);
      myOnNewSdkAdded.consume(jdk);
    }, myCreationFilter);

    List<ActionJdkItem> actions = new ArrayList<>();
    for (AnAction child : group.getChildren(null)) {
      actions.add(new ActionJdkItem(child));
    }

    myModel.setActions(actions);
  }

  private void executeNewSdkAction(@NotNull AnAction action){
    final DataContext dataContext = DataManager.getInstance().getDataContext(this);
    final AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0);
    action.actionPerformed(event);
  }

  private void attachNewDetectedSdk(@NotNull SuggestedJdkItem item) {
    String path = item.getPath();
    SdkType type = item.getSdkType();

    Sdk[] newSdk = new Sdk[1];
    mySdkModel.addSdk(type, path, sdk -> {
      reloadModel();
      newSdk[0] = sdk;
    });

    Sdk sdk = newSdk[0];
    if (sdk == null) return;
    setSelectedItem(sdk);
    myOnNewSdkAdded.consume(sdk);
  }

  public void setEditButton(final JButton editButton, final Project project, final Computable<? extends Sdk> retrieveJDK){
    editButton.addActionListener(e -> {
      final Sdk projectJdk = retrieveJDK.compute();
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
    myModel.setAndSelectInvalidJdk(name);
  }

  public void showProjectSdkItem() {
    myModel.setFirstItem(new ProjectJdkComboBoxItem());
  }

  public void showAndSelectProjectSdkItem() {
    JdkComboBoxItem item = myModel.setFirstItem(new ProjectJdkComboBoxItem());
    setSelectedItem(item);
  }

  public void showNoneSdkItem() {
    myModel.setFirstItem(new NoneJdkComboBoxItem());
  }

  public void reloadModel() {
    myModel.reload();
  }

  /**
   * @deprecated use {@link #reloadModel()}, you may also need to call
   * {@link #showNoneSdkItem()} or {@link #showProjectSdkItem()} once
   */
  @Deprecated
  @SuppressWarnings("unused")
  public void reloadModel(JdkComboBoxItem firstItem, @Nullable Project project) {
    myModel.setFirstItem(firstItem);
    myModel.reload();
  }

  @Override
  public void firePopupMenuWillBecomeVisible() {
    resolveSuggestionsIfNeeded();
    super.firePopupMenuWillBecomeVisible();
  }

  private void resolveSuggestionsIfNeeded() {
    collectNewSdkActions();
    JdksDetector.getInstance().getDetectedSdksWithUpdate(myProject, this, myDetectedSdksListener);
  }

  private final JdksDetector.DetectedSdksListener myDetectedSdksListener = new JdksDetector.DetectedSdksListener() {
    @Override
    public void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home) {
      myModel.addSuggestedItem(new SuggestedJdkItem(type, version, home));
    }

    @Override
    public void onSearchStarted() {
      myModel.removeAllSuggestedItemsAndEnableProgress();
    }

    @Override
    public void onSearchCompleted() {
      myModel.onSuggestedItemsProgressCompleted();
    }
  };

  @Override
  public void setSelectedItem(Object anObject) {
    JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    if (anObject == null) {
      showAndSelectProjectSdkItem();
      return;
    }

    if (anObject instanceof Sdk) {
      int idx = model.firstIndexOf(it -> Objects.equals(it.getJdk(), anObject));
      if (idx >= 0) {
        setSelectedItem(model.getElementAt(idx));
      }
      return;
    }

    if (anObject instanceof LoadingJdkComboBoxItem) return;

    if (anObject instanceof ActionJdkItem) {
      AnAction action = ((ActionJdkItem)anObject).getAction();
      executeNewSdkAction(action);
      return;
    }

    if (anObject instanceof SuggestedJdkItem) {
      attachNewDetectedSdk((SuggestedJdkItem)anObject);
      return;
    }

    if (!(anObject instanceof JdkComboBoxItem)) return;

    super.setSelectedItem(anObject);
  }

  /**
   * This model is used implicitly by the clients of the JdkComboBox class, be careful!
   */
  private class JdkComboBoxModelBuilder {
    private JdkComboBoxItem myFirstItem = null;
    private ImmutableList<JdkComboBoxItem> myHead = ImmutableList.of();
    private ImmutableList<ActionJdkItem> myActions = ImmutableList.of();
    private boolean myIsSdkDetectorInProgress = true;
    private ImmutableList<SuggestedJdkItem> mySuggestions = ImmutableList.of();
    private InvalidJdkComboBoxItem myInvalidJdkItem = null;

    @Nullable
    JdkComboBoxItem setFirstItem(@NotNull JdkComboBoxItem firstItem) {
      if (Objects.equals(myFirstItem, firstItem)) return myFirstItem;
      myFirstItem = firstItem;
      syncModel();
      return firstItem;
    }

    void addSuggestedItem(@NotNull SuggestedJdkItem item) {
      mySuggestions = ImmutableList.<SuggestedJdkItem>builder()
        .addAll(mySuggestions)
        .add(item)
        .build();
      syncModel();
    }

    private boolean isApplicableSuggestedItem(@NotNull SuggestedJdkItem item) {
      if (!mySdkTypeFilter.value(item.getSdkType())) return false;
      for (Sdk sdk : mySdkModel.getSdks()) {
        String sdkPath = sdk.getHomePath();
        if (sdkPath == null) continue;
        if (FileUtil.filesEqual(new File(sdkPath), new File(item.getPath()))) return false;
      }
      return true;
    }

    public void removeAllSuggestedItemsAndEnableProgress() {
      if (mySuggestions.isEmpty() && myIsSdkDetectorInProgress) return;
      mySuggestions = ImmutableList.of();
      myIsSdkDetectorInProgress = true;
      syncModel();
    }

    void onSuggestedItemsProgressCompleted() {
      if (!myIsSdkDetectorInProgress) return;
      myIsSdkDetectorInProgress = false;
      syncModel();
    }

    public void setAndSelectInvalidJdk(String name) {
      if (myInvalidJdkItem != null && Objects.equals(myInvalidJdkItem.getSdkName(), name)) return;
      myInvalidJdkItem = new InvalidJdkComboBoxItem(name);
      syncModel();
      setSelectedItem(myInvalidJdkItem);
    }

    public void syncModel() {
      Object previousSelection = getSelectedItem();
      JdkComboBoxModel newModel = new JdkComboBoxModel();
      if (myFirstItem != null) {
        newModel.addElement(myFirstItem);
      }

      myHead.forEach(newModel::addElement);
      if (myInvalidJdkItem != null) {
        newModel.addElement(myInvalidJdkItem);
      }

      myActions.forEach(newModel::addElement);
      if (myIsSdkDetectorInProgress) {
        newModel.addElement(new LoadingJdkComboBoxItem());
      }
      for (SuggestedJdkItem item : mySuggestions) {
        if (!isApplicableSuggestedItem(item)) continue;
        newModel.addElement(item);
      }
      newModel.setSelectedItem(previousSelection);
      setModel(newModel);
    }

    void setActions(@NotNull List<ActionJdkItem> actions) {
      myActions = ImmutableList.copyOf(actions);
    }

    void reload() {
      List<JdkComboBoxItem> newHead = new ArrayList<>();
      Sdk[] jdks = sortSdks(mySdkModel.getSdks());
      for (Sdk jdk : jdks) {
        if (mySdkFilter.value(jdk)) {
          newHead.add(new ActualJdkComboBoxItem(jdk));
        }
      }

      myHead = ImmutableList.copyOf(newHead);
      syncModel();
    }
  }

  private static class JdkComboBoxModel extends DefaultComboBoxModel<JdkComboBoxItem> {
    @Override
    public void setSelectedItem(Object anObject) {
      if (!(anObject instanceof JdkComboBoxItem)) return;

      int idx = firstIndexOf(it -> Objects.equals(it, anObject));
      if (idx >= 0) {
        super.setSelectedItem(getElementAt(idx));
      } else {
        super.setSelectedItem(anObject);
      }
    }

    private int firstIndexOf(@NotNull Predicate<JdkComboBoxItem> predicate) {
      for (int i = 0; i < getSize(); i++) {
        JdkComboBoxItem item = getElementAt(i);
        if (item != null && predicate.apply(item)) {
          return i;
        }
      }
      return -1;
    }

    private int lastIndexOf(@NotNull Predicate<JdkComboBoxItem> predicate) {
      for (int i = getSize() - 1; i >= 0; i--) {
        JdkComboBoxItem item = getElementAt(i);
        if (item != null && predicate.apply(item)) {
          return i;
        }
      }
      return -1;
    }

    @Nullable
    public String getSeparatorTextAbove(@Nullable JdkComboBoxItem value) {
      int valueIndex = firstIndexOf(it -> it == value);
      if (value instanceof ActionJdkItem && valueIndex > 0 && !(getElementAt(valueIndex-1) instanceof ActionJdkItem)) {
        return "";
      }

      int loadingItemIndex = lastIndexOf(LoadingJdkComboBoxItem.class::isInstance);
      if (loadingItemIndex >= 0) {
        if (value == getElementAt(loadingItemIndex)) {
          return ProjectBundle.message("jdk.combo.box.autodetected");
        }
      } else {
        if (value instanceof SuggestedJdkItem && valueIndex > 0 && !(getElementAt(valueIndex-1) instanceof SuggestedJdkItem)) {
          return ProjectBundle.message("jdk.combo.box.autodetected");
        }
      }

      return null;
    }
  }

  @NotNull
  private static Sdk[] sortSdks(@NotNull final Sdk[] sdks) {
    Sdk[] clone = sdks.clone();
    Arrays.sort(clone, (sdk1, sdk2) -> {
      SdkType sdkType1 = (SdkType)sdk1.getSdkType();
      SdkType sdkType2 = (SdkType)sdk2.getSdkType();
      if (!sdkType1.getComparator().equals(sdkType2.getComparator())) return StringUtil.compare(sdkType1.getPresentableName(), sdkType2.getPresentableName(), true);
      return sdkType1.getComparator().compare(sdk1, sdk2);
    });
    return clone;
  }

  @NotNull
  public static Condition<Sdk> getSdkFilter(@Nullable final Condition<? super SdkTypeId> filter) {
    return filter == null ? Conditions.alwaysTrue() : sdk -> filter.value(sdk.getSdkType());
  }

  @NotNull
  private static <T> Condition<T> matchAllIfNull(@Nullable Condition<? super T> condition) {
    if (condition == null) return it -> it != null;
    return it -> it != null && condition.value(it);
  }

  @NotNull
  private static <T> Consumer<T> emptyIfNull(@Nullable Consumer<? super T> consumer) {
    if (consumer == null) return it -> {};
    return it -> {
      if (it != null) {
        consumer.consume(it);
      }
    };
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

  public static class LoadingJdkComboBoxItem extends JdkComboBoxItem {

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

    @Nullable
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

  public static class ProjectJdkComboBoxItem extends JdkComboBoxItem {
    public String toString() {
      return ProjectBundle.message("jdk.combo.box.project.item");
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

  private static class InvalidJdkComboBoxItem extends JdkComboBoxItem {
    private final String mySdkName;

    InvalidJdkComboBoxItem(String name) {
      mySdkName = name;
    }

    @Override
    public String getSdkName() {
      return mySdkName;
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.invalid.item", mySdkName);
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
  }

  private static final class ActionJdkItem extends JdkComboBoxItem {
    private final AnAction myAction;

    ActionJdkItem(@NotNull AnAction action) {
      myAction = action;
    }

    @NotNull
    AnAction getAction() {
      return myAction;
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
