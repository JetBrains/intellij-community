/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.ListItemDescriptor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.ArchivedProjectTemplate;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 12/24/13
 */
public class ProjectTemplateList extends JPanel {

  private JBList myList;
  private JPanel myPanel;
  private JTextPane myDescriptionPane;
  private ProjectTemplate myFirstArchivedTemplate;

  public ProjectTemplateList() {
    super(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);

    myList.setCellRenderer(new GroupedItemsListRenderer(new ListItemDescriptor<ProjectTemplate>() {
      @Nullable
      @Override
      public String getTextFor(ProjectTemplate value) {
        return value.getName();
      }

      @Nullable
      @Override
      public String getTooltipFor(ProjectTemplate value) {
        return null;
      }

      @Nullable
      @Override
      public Icon getIconFor(ProjectTemplate value) {
        return value.getIcon();
      }

      @Override
      public boolean hasSeparatorAboveOf(ProjectTemplate value) {
        return value == myFirstArchivedTemplate;
      }

      @Nullable
      @Override
      public String getCaptionAboveOf(ProjectTemplate value) {
        return "Project Templates";
      }
    }));


    myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
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
    });

    Messages.installHyperlinkSupport(myDescriptionPane);
  }

  public void setTemplates(List<ProjectTemplate> list) {
    Collections.sort(list, new Comparator<ProjectTemplate>() {
      @Override
      public int compare(ProjectTemplate o1, ProjectTemplate o2) {
        return Comparing.compare(o1 instanceof ArchivedProjectTemplate, o2 instanceof ArchivedProjectTemplate);
      }
    });
    myFirstArchivedTemplate = ContainerUtil.find(list, new Condition<ProjectTemplate>() {
      @Override
      public boolean value(ProjectTemplate template) {
        return template instanceof ArchivedProjectTemplate;
      }
    });

    int index = myList.getSelectedIndex();
    //noinspection unchecked
    myList.setModel(new CollectionListModel(list));
    myList.setSelectedIndex(index == -1 ? 0 : index);
  }

  public ProjectTemplate getSelectedTemplate() {
    return (ProjectTemplate)myList.getSelectedValue();
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    myList.addListSelectionListener(listener);
  }

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

  public void setPaintBusy(boolean b) {
    myList.setPaintBusy(b);
  }
}
