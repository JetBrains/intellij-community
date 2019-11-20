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
package com.intellij.openapi.roots.ui.configuration;

import com.google.common.collect.ImmutableList;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.containers.Predicate;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.projectRoots.SimpleJavaSdkType.notSimpleJavaSdkType;
import static com.intellij.ui.AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED;

/**
 * @author Eugene Zhuravlev
 */
public class JdkComboBox extends ComboBox<JdkComboBox.JdkComboBoxItem> {

  private static final Icon EMPTY_ICON = EmptyIcon.create(1, 16);

  @Nullable
  private final Condition<? super Sdk> myFilter;
  @Nullable
  private final Condition<SdkTypeId> myCreationFilter;
  private JButton mySetUpButton;
  @Nullable
  private final Condition<? super SdkTypeId> mySdkTypeFilter;
  @NotNull
  private final JdkComboBoxModel myModel;

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel) {
    this(jdkModel, null);
  }

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<? super SdkTypeId> filter) {
    this(jdkModel, filter, getSdkFilter(filter), filter, false);
  }

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                     @Nullable Condition<? super Sdk> filter,
                     @Nullable Condition<? super SdkTypeId> creationFilter,
                     boolean addSuggestedItems) {
    super();
    myFilter = filter;
    mySdkTypeFilter = sdkTypeFilter;
    myCreationFilter = getCreationFilter(creationFilter);

    setMinimumAndPreferredWidth(JBUI.scale(500));
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
            //TODO[jo] add an item per provider here?
            append("Looking for JDKs...");
          }
          else if (value instanceof ProjectJdkComboBoxItem) {
            final Sdk jdk = jdkModel.getProjectSdk();
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

    myModel = new JdkComboBoxModel();
    myModel.reload(null, jdkModel, sdkTypeFilter, filter, addSuggestedItems);
    setModel(myModel);
  }

  @NotNull
  private static Condition<SdkTypeId> getCreationFilter(@Nullable Condition<? super SdkTypeId> creationFilter) {
    return notSimpleJavaSdkType(creationFilter);
  }

  /**
   * TODO[jo]
   */
  @Deprecated
  public void setSetupButton(final JButton setUpButton,
                                @Nullable final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<? super Sdk> additionalSetup,
                                final boolean moduleJdkSetup) {
    setSetupButton(setUpButton, project, jdksModel, firstItem, additionalSetup,
                   ProjectBundle.message("project.roots.set.up.jdk.title", moduleJdkSetup ? 1 : 2));
  }

  /**
   * TODO[jo]
   */
  @Deprecated
  public void setSetupButton(final JButton setUpButton,
                                @Nullable final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<? super Sdk> additionalSetup,
                                final String actionGroupTitle) {

    mySetUpButton = setUpButton;
    mySetUpButton.setVisible(false);

    //TODO: move actions computation to the dialog popup open!
    DefaultActionGroup group = new DefaultActionGroup();
    jdksModel.createAddActions(group, this, getSelectedJdk(), jdk -> {
      if (project != null) {
        final JdkListConfigurable configurable = JdkListConfigurable.getInstance(project);
        configurable.addJdkNode(jdk, false);
      }
      reloadModel(new ActualJdkComboBoxItem(jdk), project);
      setSelectedJdk(jdk); //restore selection
      if (additionalSetup != null) {
        if (additionalSetup.value(jdk)) { //leave old selection
          setSelectedJdk(firstItem.getJdk());
        }
      }
    }, myCreationFilter);

    List<ActionJdkItem> actions = new ArrayList<>();
    for (AnAction child : group.getChildren(null)) {
      actions.add(new ActionJdkItem(child) {
        @Override
        protected void executeAction() {
          final DataContext dataContext = DataManager.getInstance().getDataContext(JdkComboBox.this);
          final AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0);
          getAction().actionPerformed(event);
        }
      });
    }

    myModel.setActions(actions);
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
   * @deprecated the pupup shown by the SetUp button is now included
   * directly into the popup, you may remove the button from your UI,
   * see {@link #setSetupButton(JButton, Project, ProjectSdksModel, JdkComboBoxItem, Condition, boolean)}
   * for more details
   */
  @Nullable
  @Deprecated
  public JButton getSetUpButton() {
    return mySetUpButton;
  }

  @Override
  public JdkComboBoxItem getSelectedItem() {
    return (JdkComboBoxItem)super.getSelectedItem();
  }

  @Nullable
  public Sdk getSelectedJdk() {
    final JdkComboBoxItem selectedItem = getSelectedItem();
    return selectedItem != null? selectedItem.getJdk() : null;
  }

  public void setSelectedJdk(Sdk jdk) {
    final int index = indexOf(jdk);
    if (index >= 0) {
      setSelectedIndex(index);
    }
  }

  public void setInvalidJdk(String name) {
    removeInvalidElement();
    addItem(new InvalidJdkComboBoxItem(name));
    setSelectedIndex(getModel().getSize() - 1);
  }

  private int indexOf(@Nullable Sdk jdk) {
    //TODO[jo]: reuse code!
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final JdkComboBoxItem elementAt = model.getElementAt(idx);
      if (jdk == null) {
        if (elementAt instanceof NoneJdkComboBoxItem || elementAt instanceof ProjectJdkComboBoxItem) {
          return idx;
        }
      }
      else {
        Sdk elementAtJdk = elementAt.getJdk();
        if (elementAtJdk != null && jdk.getName().equals(elementAtJdk.getName())) {
          return idx;
        }
      }
    }
    return -1;
  }

  private void removeInvalidElement() {
    //TODO[jo] review
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    final int count = model.getSize();
    for (int idx = 0; idx < count; idx++) {
      final JdkComboBoxItem elementAt = model.getElementAt(idx);
      if (elementAt instanceof InvalidJdkComboBoxItem) {
        removeItemAt(idx);
        break;
      }
    }
  }

  public void reloadModel(JdkComboBoxItem firstItem, @Nullable Project project) {
    final JdkComboBoxModel model = (JdkComboBoxModel)getModel();
    if (project == null) {
      model.addElement(firstItem);
      return;
    }
    model.reload(firstItem, ProjectStructureConfigurable.getInstance(project).getProjectJdksModel(), mySdkTypeFilter, myFilter, false);
  }


  @Override
  public void firePopupMenuWillBecomeVisible() {
    resolveSuggestionsIfNeeded();
    super.firePopupMenuWillBecomeVisible();
  }

  private void resolveSuggestionsIfNeeded() {
    //TODO[jo]: track Disposable
    JdksDetector.getInstance().getDetectedSdksWithUpdate(Disposer.newDisposable(), this, new JdksDetector.DetectedSdksListener() {
      @Override
      public void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home) {
        if (mySdkTypeFilter == null || !mySdkTypeFilter.value(type)) return;
        myModel.addSuggestedItem(new SuggestedJdkItem(type, version, home));
      }

      @Override
      public void setSearchIsRunning(boolean running) {
        myModel.onSuggestedItemsProgress(running);
      }

      @Override
      public void onSearchRestarted() {
        myModel.removeAllSuggestedItems();
      }
    });
  }

  /**
   * This model is used implicitly by the clients of the JdkComboBox class, be careful!
   */
  private static class JdkComboBoxModel extends DefaultComboBoxModel<JdkComboBoxItem> {
    private ImmutableList<JdkComboBoxItem> myHead = ImmutableList.of();
    private ImmutableList<ActionJdkItem> myActions = ImmutableList.of();
    private boolean myIsSdkDetectorInProgress = true;
    private ImmutableList<SuggestedJdkItem> mySuggestions = ImmutableList.of();

    void addSuggestedItem(@NotNull SuggestedJdkItem item) {
      //TODO[jo] sort found items
      mySuggestions = ImmutableList.<SuggestedJdkItem>builder()
        .addAll(mySuggestions).add(item).build();
      reload(false);
    }

    public void removeAllSuggestedItems() {
      mySuggestions = ImmutableList.of();
      reload(false);
    }

    void onSuggestedItemsProgress(boolean running) {
      myIsSdkDetectorInProgress = running;
      reload(false);
    }

    public void reload(boolean recoverHead) {
      //the model is patched from outside of the JdkComboBox, an attempt to preserve the first element
      JdkComboBoxItem hackElement = recoverHead && getSize() > 0 ? getElementAt(0) : null;
      //do we have the head element at the beginning of the list?
      if (hackElement != null && !myHead.isEmpty() && Objects.equals(hackElement, myHead.get(0))) hackElement = null;
      removeAllElements();

      if (hackElement != null) {
        addElement(hackElement);
      }

      myHead.forEach(this::addElement);
      myActions.forEach(this::addElement);
      if (myIsSdkDetectorInProgress) {
        addElement(new LoadingJdkComboBoxItem());
      }
      mySuggestions.forEach(this::addElement);
    }

    void setActions(@NotNull List<ActionJdkItem> actions) {
      myActions = ImmutableList.copyOf(actions);
    }

    void reload(@Nullable final JdkComboBoxItem firstItem,
                @NotNull final ProjectSdksModel jdksModel,
                @Nullable Condition<? super SdkTypeId> sdkTypeFilter,
                @Nullable Condition<? super Sdk> sdkFilter,
                boolean addSuggested) {
      List<JdkComboBoxItem> newHead = new ArrayList<>();
      if (firstItem != null) newHead.add(firstItem);

      Sdk[] jdks = sortSdks(jdksModel.getSdks());
      for (Sdk jdk : jdks) {
        if (sdkFilter == null || sdkFilter.value(jdk)) {
          newHead.add(new ActualJdkComboBoxItem(jdk));
        }
      }

      myHead = ImmutableList.copyOf(newHead);
      reload(true);
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
          return "Autodetected";
        }
      } else {
        if (value instanceof SuggestedJdkItem && valueIndex > 0 && !(getElementAt(valueIndex-1) instanceof SuggestedJdkItem)) {
          return "Autodetected";
        }
      }

      return null;
    }

    @Override
    public void setSelectedItem(Object anObject) {
      if (!(anObject instanceof JdkComboBoxItem)) return;
      if (anObject instanceof LoadingJdkComboBoxItem) return;

      if (anObject instanceof ActionJdkItem) {
        ((ActionJdkItem)anObject).executeAction();
        return;
      }

      super.setSelectedItem(anObject);
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
  }

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

  ///TODO: an option to track this class either as exportable or as internal
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

  private static abstract class ActionJdkItem extends JdkComboBoxItem {
    private final AnAction myAction;

    private ActionJdkItem(@NotNull AnAction action) {
      myAction = action;
    }

    @NotNull
    public AnAction getAction() {
      return myAction;
    }

    protected abstract void executeAction();
  }
}
