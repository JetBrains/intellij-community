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

import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.ProjectJdkListRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkListConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBoxWithWidePopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 * @since May 18, 2005
 */
public class JdkComboBox extends ComboBoxWithWidePopup {

  private static final Icon EMPTY_ICON = EmptyIcon.create(1, 16);

  @Nullable
  private final Condition<Sdk> myFilter;
  @Nullable
  private final Condition<SdkTypeId> myCreationFilter;
  private JButton mySetUpButton;

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel) {
    this(jdkModel, null);
  }

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<SdkTypeId> filter) {
    this(jdkModel, filter, getSdkFilter(filter), filter, false);
  }

  public JdkComboBox(@NotNull final ProjectSdksModel jdkModel,
                     @Nullable Condition<SdkTypeId> sdkTypeFilter,
                     @Nullable Condition<Sdk> filter,
                     @Nullable Condition<SdkTypeId> creationFilter,
                     boolean addSuggestedItems) {
    super(new JdkComboBoxModel(jdkModel, sdkTypeFilter, filter, addSuggestedItems));
    myFilter = filter;
    myCreationFilter = creationFilter;
    setRenderer(new ProjectJdkListRenderer() {
      @Override
      public void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (JdkComboBox.this.isEnabled()) {
          setIcon(EMPTY_ICON);    // to fix vertical size
          if (value instanceof InvalidJdkComboBoxItem) {
            final String str = value.toString();
            append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
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
            String version = type.getVersionString(home);
            append(version == null ? type.getPresentableName() : version);
            append(" (" + home + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
          }
          else {
            super.doCustomize(list, value != null ? ((JdkComboBoxItem)value).getJdk()
                                                  : new NoneJdkComboBoxItem(), index, selected, hasFocus);
          }
        }
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    final Rectangle rec = ScreenUtil.getScreenRectangle(0, 0);
    final Dimension size = super.getPreferredSize();
    final int maxWidth = rec.width / 4;
    if (size.width > maxWidth) {
      size.width = maxWidth; 
    }
    return size;
  }

  @Override
  public Dimension getMinimumSize() {
    final Dimension minSize = super.getMinimumSize();
    final Dimension prefSize = getPreferredSize();
    if (minSize.width > prefSize.width) {
      minSize.width = prefSize.width;
    }
    return minSize;
  }

  public void setSetupButton(final JButton setUpButton,
                                @Nullable final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<Sdk> additionalSetup,
                                final boolean moduleJdkSetup) {
    setSetupButton(setUpButton, project, jdksModel, firstItem, additionalSetup,
                   ProjectBundle.message("project.roots.set.up.jdk.title", moduleJdkSetup ? 1 : 2));
  }

  public void setSetupButton(final JButton setUpButton,
                                @Nullable final Project project,
                                final ProjectSdksModel jdksModel,
                                final JdkComboBoxItem firstItem,
                                @Nullable final Condition<Sdk> additionalSetup,
                                final String actionGroupTitle) {

    mySetUpButton = setUpButton;
    mySetUpButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        DefaultActionGroup group = new DefaultActionGroup();
        jdksModel.createAddActions(group, JdkComboBox.this, jdk -> {
          if (project != null) {
            final JdkListConfigurable configurable = JdkListConfigurable.getInstance(project);
            configurable.addJdkNode(jdk, false);
          }
          reloadModel(new JdkComboBoxItem(jdk), project);
          setSelectedJdk(jdk); //restore selection
          if (additionalSetup != null) {
            if (additionalSetup.value(jdk)) { //leave old selection
              setSelectedJdk(firstItem.getJdk());
            }
          }
        }, myCreationFilter);
        final DataContext dataContext = DataManager.getInstance().getDataContext(JdkComboBox.this);
        if (group.getChildrenCount() > 1) {
          JBPopupFactory.getInstance()
            .createActionGroupPopup(actionGroupTitle, group, dataContext, JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
            .showUnderneathOf(setUpButton);
        }
        else {
          final AnActionEvent event =
            new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(""), ActionManager.getInstance(), 0);
          group.getChildren(event)[0].actionPerformed(event);
        }
      }
    });
  }

  public void setEditButton(final JButton editButton, final Project project, final Computable<Sdk> retrieveJDK){
    editButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final Sdk projectJdk = retrieveJDK.compute();
        if (projectJdk != null) {
          ProjectStructureConfigurable.getInstance(project).select(projectJdk, true);
        }
      }
    });
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final JdkComboBoxItem selectedItem = getSelectedItem();
        if (selectedItem instanceof ProjectJdkComboBoxItem) {
          editButton.setEnabled(ProjectStructureConfigurable.getInstance(project).getProjectJdksModel().getProjectSdk() != null);
        }
        else {
          editButton.setEnabled(!(selectedItem instanceof InvalidJdkComboBoxItem) && selectedItem != null && selectedItem.getJdk() != null);
        }
      }
    });
  }

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
  
  private int indexOf(Sdk jdk) {
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
    final DefaultComboBoxModel model = ((DefaultComboBoxModel)getModel());
    if (project == null) {
      model.addElement(firstItem);
      return;
    }
    model.removeAllElements();
    model.addElement(firstItem);
    final ProjectSdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
    List<Sdk> projectJdks = new ArrayList<>(projectJdksModel.getProjectSdks().values());
    if (myFilter != null) {
      projectJdks = ContainerUtil.filter(projectJdks, myFilter);
    }
    Collections.sort(projectJdks, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
    for (Sdk projectJdk : projectJdks) {
      model.addElement(new JdkComboBox.JdkComboBoxItem(projectJdk));
    }
  }

  private static class JdkComboBoxModel extends DefaultComboBoxModel {
    public JdkComboBoxModel(final ProjectSdksModel jdksModel, @Nullable Condition<SdkTypeId> sdkTypeFilter,
                            @Nullable Condition<Sdk> sdkFilter, boolean addSuggested) {
      Sdk[] jdks = jdksModel.getSdks();
      Arrays.sort(jdks, (s1, s2) -> s1.getName().compareToIgnoreCase(s2.getName()));
      for (Sdk jdk : jdks) {
        if (sdkFilter == null || sdkFilter.value(jdk)) {
          addElement(new JdkComboBoxItem(jdk));
        }
      }
      if (addSuggested) {
        addSuggestedItems(sdkTypeFilter, jdks);
      }
    }

    protected void addSuggestedItems(@Nullable Condition<SdkTypeId> sdkTypeFilter, Sdk[] jdks) {
      SdkType[] types = SdkType.getAllTypes();
      for (SdkType type : types) {
        if (sdkTypeFilter == null || sdkTypeFilter.value(type) && ContainerUtil.find(jdks, sdk -> sdk.getSdkType() == type) == null) {
          String homePath = type.suggestHomePath();
          if (homePath != null && type.isValidSdkHome(homePath)) {
            addElement(new SuggestedJdkItem(type, homePath));
          }
        }
      }
    }

    // implements javax.swing.ListModel
    @Override
    public JdkComboBoxItem getElementAt(int index) {
      return (JdkComboBoxItem)super.getElementAt(index);
    }
  }

  public static Condition<Sdk> getSdkFilter(@Nullable final Condition<SdkTypeId> filter) {
    return filter == null ? Conditions.<Sdk>alwaysTrue() : (Condition<Sdk>)sdk -> filter.value(sdk.getSdkType());
  }

  public static class JdkComboBoxItem {
    private final Sdk myJdk;

    public JdkComboBoxItem(@Nullable Sdk jdk) {
      myJdk = jdk;
    }

    public Sdk getJdk() {
      return myJdk;
    }

    public SdkType getSdkType() { return myJdk == null ? null : (SdkType)myJdk.getSdkType(); }

    @Nullable
    public String getSdkName() {
      return myJdk != null ? myJdk.getName() : null;
    }
    
    public String toString() {
      return myJdk.getName();
    }
  }

  public static class ProjectJdkComboBoxItem extends JdkComboBoxItem {
    public ProjectJdkComboBoxItem() {
      super(null);
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.project.item");
    }
  }

  public static class NoneJdkComboBoxItem extends JdkComboBoxItem {
    public NoneJdkComboBoxItem() {
      super(null);
    }

    public String toString() {
      return ProjectBundle.message("jdk.combo.box.none.item");
    }
  }

  private static class InvalidJdkComboBoxItem extends JdkComboBoxItem {
    private final String mySdkName;

    public InvalidJdkComboBoxItem(String name) {
      super(null);
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

  public static class SuggestedJdkItem extends JdkComboBoxItem {
    private final SdkType mySdkType;
    private final String myPath;

    public SuggestedJdkItem(SdkType sdkType, @NotNull String path) {
      super(null);
      mySdkType = sdkType;
      myPath = path;
    }

    @Override
    public SdkType getSdkType() {
      return mySdkType;
    }

    public String getPath() {
      return myPath;
    }
  }
}
