/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 12/24/13
 */
public class ProjectTemplateList extends JPanel {

  private static final String PROJECT_WIZARD_TEMPLATE = "project.wizard.template";

  private JBList<ProjectTemplate> myList;
  private JPanel myPanel;
  private JTextPane myDescriptionPane;

  public ProjectTemplateList() {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    GroupedItemsListRenderer<ProjectTemplate> renderer = new GroupedItemsListRenderer<ProjectTemplate>(new ListItemDescriptorAdapter<ProjectTemplate>() {
      @Nullable
      @Override
      public String getTextFor(ProjectTemplate value) {
        return value.getName();
      }

      @Nullable
      @Override
      public Icon getIconFor(ProjectTemplate value) {
        return value.getIcon();
      }
    }) {

      @Override
      protected void customizeComponent(JList<? extends ProjectTemplate> list, ProjectTemplate value, boolean isSelected) {
        super.customizeComponent(list, value, isSelected);
        Icon icon = myTextLabel.getIcon();
        if (icon != null && myTextLabel.getDisabledIcon() == icon) {
          myTextLabel.setDisabledIcon(IconLoader.getDisabledIcon(icon));
        }
        myTextLabel.setEnabled(myList.isEnabled());
        myTextLabel.setBorder(JBUI.Borders.empty(3, 3, 3, 3));
      }
    };
    myList.setCellRenderer(renderer);
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateSelection();
      }
    });

    Messages.installHyperlinkSupport(myDescriptionPane);
  }

  private void updateSelection() {
    myDescriptionPane.setText("");
    ProjectTemplate template = getSelectedTemplate();
    if (template != null) {
      String description = template.getDescription();
      if (StringUtil.isNotEmpty(description)) {
        description = "<html><body><font " +
                      (SystemInfo.isMac ? "" : "face=\"Verdana\" size=\"-1\"") + '>' + description +
                      "</font></body></html>";
        myDescriptionPane.setText(description);
      }
    }
  }

  public void setTemplates(List<ProjectTemplate> list, boolean preserveSelection) {
    Collections.sort(list, (o1, o2) -> Comparing.compare(o1 instanceof ArchivedProjectTemplate, o2 instanceof ArchivedProjectTemplate));

    int index = preserveSelection ? myList.getSelectedIndex() : -1;
    //noinspection unchecked
    myList.setModel(new CollectionListModel<>(list));
    if (myList.isEnabled()) {
      myList.setSelectedIndex(index == -1 ? 0 : index);
    }
    updateSelection();
  }

  @Nullable
  public ProjectTemplate getSelectedTemplate() {
    return myList.getSelectedValue();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myList.setEnabled(enabled);
    if (!enabled) {
      myList.clearSelection();
    }
    else {
      myList.setSelectedIndex(0);
    }
    myDescriptionPane.setEnabled(enabled);
  }

  void restoreSelection() {
    final String templateName = PropertiesComponent.getInstance().getValue(PROJECT_WIZARD_TEMPLATE);
    if (templateName != null && myList.getModel() instanceof CollectionListModel) {
      @SuppressWarnings("unchecked")
      List<ProjectTemplate> list = ((CollectionListModel<ProjectTemplate>)myList.getModel()).toList();
      ProjectTemplate template = ContainerUtil.find(list, template1 -> templateName.equals(template1.getName()));
      if (template != null) {
        myList.setSelectedValue(template, true);
      }
    }
    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        ProjectTemplate template = getSelectedTemplate();
        if (template != null) {
          PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_TEMPLATE, template.getName());
        }
      }
    });
  }

  public JBList getList() {
    return myList;
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myList.addListSelectionListener(listener);
  }

  public void setPaintBusy(boolean b) {
    myList.setPaintBusy(b);
  }

  @TestOnly
  public boolean setSelectedTemplate(String name) {
    ListModel model1 = myList.getModel();
    for (int j = 0; j < model1.getSize(); j++) {
      if (name.equals(((ProjectTemplate)model1.getElementAt(j)).getName())) {
        myList.setSelectedIndex(j);
        return true;
      }
    }

    return false;
  }
}
