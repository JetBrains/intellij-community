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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.ProjectJdkListRenderer;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.JdkListConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2005
 */
class JdkComboBox extends JComboBox{
  private final JButton myEditButton = new JButton(ApplicationBundle.message("button.edit"));

  public JdkComboBox(final ProjectJdksModel jdksModel) {
    super(new JdkComboBoxModel(jdksModel));
    setRenderer(new ProjectJdkListRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (JdkComboBox.this.isEnabled()) {
          if (value instanceof InvalidJdkComboBoxItem) {
            final String str = value.toString();
            append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
          }
          else if (value instanceof ProjectJdkComboBoxItem){
            final ProjectJdkComboBoxItem item = (ProjectJdkComboBoxItem)value;
            final String str = item.toString();
            final Sdk jdk = jdksModel.getProjectJdk();
            if (jdk != null){
              setIcon(jdk.getSdkType().getIcon());
              append(ProjectBundle.message("project.roots.project.jdk.inherited"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              append(" (" + jdk.getName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else {
              append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
          }
          else {
            super.customizeCellRenderer(list, value != null ? ((JdkComboBoxItem)value).getJdk() : new NoneJdkComboBoxItem(), index, selected, hasFocus);
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

  public JButton createSetupButton(final Project project, final ProjectJdksModel jdksModel, final JdkComboBoxItem firstItem) {
    return createSetupButton(project, jdksModel, firstItem, null, false);
  }


  public JButton createSetupButton(final Project project,
                                   final ProjectJdksModel jdksModel,
                                   final JdkComboBoxItem firstItem,
                                   final Condition<Sdk> additionalSetup,
                                   final boolean moduleJdkSetup) {
    final JButton setUpButton = new JButton(ApplicationBundle.message("button.new"));
    setUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final JdkListConfigurable configurable = JdkListConfigurable.getInstance(project);
        DefaultActionGroup group = new DefaultActionGroup();
        jdksModel.createAddActions(group, JdkComboBox.this, new Consumer<Sdk>() {
          public void consume(final Sdk jdk) {
            configurable.addJdkNode(jdk, false);
            reloadModel(firstItem, project);
            setSelectedJdk(jdk); //restore selection
            if (additionalSetup != null) {
              if (additionalSetup.value(jdk)) { //leave old selection
                setSelectedJdk(firstItem.getJdk());
              }
            }
          }
        });
        JBPopupFactory.getInstance()
          .createActionGroupPopup(ProjectBundle.message("project.roots.set.up.jdk.title", moduleJdkSetup ? 1 : 2), group,
                                  DataManager.getInstance().getDataContext(), JBPopupFactory.ActionSelectionAid.MNEMONICS, false)
          .showUnderneathOf(setUpButton);
      }
    });
    return setUpButton;
  }

  public void appendEditButton(final Project project, final JPanel panel, GridBagConstraints gc, final Computable<Sdk> retrieveJDK){
    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Sdk projectJdk = retrieveJDK.compute();
        ProjectStructureConfigurable.getInstance(project).select(projectJdk, true);
      }
    });
    addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e) {
        final JdkComboBoxItem selectedItem = getSelectedItem();
        if (selectedItem instanceof ProjectJdkComboBoxItem) {
          myEditButton.setEnabled(ProjectStructureConfigurable.getInstance(project).getProjectJdksModel().getProjectJdk() != null);
        } else {
          myEditButton.setEnabled(!(selectedItem instanceof InvalidJdkComboBoxItem) && selectedItem != null && selectedItem.getJdk() != null);
        }
      }
    });
    panel.add(myEditButton, gc);
  }

  public JdkComboBoxItem getSelectedItem() {
    return (JdkComboBoxItem)super.getSelectedItem();
  }

  public Sdk getSelectedJdk() {
    final JdkComboBoxItem selectedItem = (JdkComboBoxItem)super.getSelectedItem();
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
        if (elementAt instanceof NoneJdkComboBoxItem) {
          return idx;
        } else if (elementAt instanceof ProjectJdkComboBoxItem){
          return idx;
        }
      }
      else {
        if (jdk.equals(elementAt.getJdk())) {
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

  public void reloadModel(JdkComboBoxItem firstItem, Project project) {
    final DefaultComboBoxModel model = ((DefaultComboBoxModel)getModel());
    model.removeAllElements();
    model.addElement(firstItem);
    final ProjectJdksModel projectJdksModel = ProjectStructureConfigurable.getInstance(project).getProjectJdksModel();
    final ArrayList<Sdk> projectJdks = new ArrayList<Sdk>(projectJdksModel.getProjectJdks().values());
    Collections.sort(projectJdks, new Comparator<Sdk>() {
      public int compare(final Sdk o1, final Sdk o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (Sdk projectJdk : projectJdks) {
      model.addElement(new JdkComboBox.JdkComboBoxItem(projectJdk));
    }
  }

  private static class JdkComboBoxModel extends DefaultComboBoxModel {
    public JdkComboBoxModel(final ProjectJdksModel jdksModel) {
      super();
      final Sdk[] jdks = jdksModel.getSdks();
      Arrays.sort(jdks, new Comparator<Sdk>() {
        public int compare(final Sdk s1, final Sdk s2) {
          return s1.getName().compareToIgnoreCase(s2.getName());
        }
      });
      for (Sdk jdk : jdks) {
        addElement(new JdkComboBoxItem(jdk));
      }
    }

    // implements javax.swing.ListModel
    public JdkComboBoxItem getElementAt(int index) {
      return (JdkComboBoxItem)super.getElementAt(index);
    }
  }
  
  public static class JdkComboBoxItem {
    private final Sdk myJdk;

    public JdkComboBoxItem(Sdk jdk) {
      myJdk = jdk;
    }

    public Sdk getJdk() {
      return myJdk;
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
    private final String myName;

    public InvalidJdkComboBoxItem(String name) {
      super(null);
      myName = ProjectBundle.message("jdk.combo.box.invalid.item", name);
    }

    public String toString() {
      return myName;
    }
  }
  
}
