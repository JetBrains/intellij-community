// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
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
      @NotNull
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
    myList.getSelectionModel().addListSelectionListener(__ -> updateSelection());

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

  public void setTemplates(List<? extends ProjectTemplate> list, boolean preserveSelection) {
    Collections.sort(list, (o1, o2) -> Comparing.compare(o1 instanceof ArchivedProjectTemplate, o2 instanceof ArchivedProjectTemplate));

    int index = preserveSelection ? myList.getSelectedIndex() : -1;
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
      List<ProjectTemplate> list = ((CollectionListModel<ProjectTemplate>)myList.getModel()).toList();
      ProjectTemplate template = ContainerUtil.find(list, template1 -> templateName.equals(template1.getName()));
      if (template != null) {
        myList.setSelectedValue(template, true);
      }
    }
    myList.getSelectionModel().addListSelectionListener(__ -> {
      ProjectTemplate template = getSelectedTemplate();
      if (template != null) {
        PropertiesComponent.getInstance().setValue(PROJECT_WIZARD_TEMPLATE, template.getName());
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
  boolean setSelectedTemplate(@NotNull String name) {
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
