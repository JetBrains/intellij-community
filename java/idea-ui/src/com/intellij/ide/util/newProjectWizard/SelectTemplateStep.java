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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.Condition;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 9/26/12
 */
public class SelectTemplateStep extends ModuleWizardStep {

  private JPanel myPanel;
  private JBList myTemplatesList;
  private JPanel mySettingsPanel;
  private SearchTextField mySearchField;
  private JBLabel myDescriptionLabel;
  private JPanel myDescriptionPanel;

  public SelectTemplateStep(WizardContext context) {

    final List<ProjectTemplate> templates = new ArrayList<ProjectTemplate>();
    ProjectTemplatesFactory[] factories = ProjectTemplatesFactory.EP_NAME.getExtensions();
    for (ProjectTemplatesFactory factory : factories) {
      templates.addAll(Arrays.asList(factory.createTemplates(context)));
    }

    myTemplatesList.setModel(new CollectionListModel<ProjectTemplate>(templates));
    myTemplatesList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        ProjectTemplate template = (ProjectTemplate)value;
        append(template.getName());
      }
    });

    myTemplatesList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (mySettingsPanel.getComponentCount() > 0) {
          mySettingsPanel.remove(0);
        }
        ProjectTemplate template = getSelectedTemplate();
        if (template != null) {
          JComponent settingsPanel = template.getSettingsPanel();
          if (settingsPanel != null) {
            mySettingsPanel.add(settingsPanel, BorderLayout.NORTH);
          }
          mySettingsPanel.setVisible(settingsPanel != null);
          String description = template.getDescription();
          myDescriptionLabel.setText(description);
          myDescriptionPanel.setVisible(description != null);
        }
        mySettingsPanel.revalidate();
        mySettingsPanel.repaint();
      }
    });
    if (myTemplatesList.getModel().getSize() > 0) {
      myTemplatesList.setSelectedIndex(0);
    }
    mySearchField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        final MinusculeMatcher matcher = NameUtil.buildMatcher(mySearchField.getText(), NameUtil.MatchingCaseSensitivity.NONE);
        ProjectTemplate selectedTemplate = getSelectedTemplate();
        List<ProjectTemplate> list = ContainerUtil.filter(templates, new Condition<ProjectTemplate>() {
          @Override
          public boolean value(ProjectTemplate template) {
            String name = template.getName();
            String[] words = NameUtil.nameToWords(name);
            for (String word : words) {
              if (matcher.matches(word)) return true;
            }
            return false;
          }
        });
        myTemplatesList.setModel(new CollectionListModel<ProjectTemplate>(list));
        if (!list.isEmpty()) {
          if (list.contains(selectedTemplate)) {
            myTemplatesList.setSelectedValue(selectedTemplate, true);
          }
          else {
            myTemplatesList.setSelectedIndex(0);
          }
        }
      }
    });
  }

  public ProjectTemplate getSelectedTemplate() {
    return (ProjectTemplate)myTemplatesList.getSelectedValue();
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchField;
  }

  @Override
  public void updateDataModel() {
  }
}
