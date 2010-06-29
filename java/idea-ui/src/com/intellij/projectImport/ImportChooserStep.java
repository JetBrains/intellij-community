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

/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.projectImport;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ImportChooserStep extends ProjectImportWizardStep {
  private static final String PREFFERED = "create.project.preffered.importer";

  private final StepSequence mySequence;
  private final JList myList;
  private final JPanel myPanel;

  public ImportChooserStep(final ProjectImportProvider[] providers, final StepSequence sequence, final WizardContext context) {
    super(context);
    mySequence = sequence;
    myPanel = new JPanel(new BorderLayout());
    final DefaultListModel model = new DefaultListModel();
    myList = new JBList(model);

    for (ProjectImportProvider provider : sorted(providers)) {
      model.addElement(provider);
    }
    
    myList.setCellRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component rendererComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        setText(((ProjectImportProvider)value).getName());
        setIcon(((ProjectImportProvider)value).getIcon());
        return rendererComponent;
      }
    });
    myPanel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);
    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        final ProjectImportProvider provider = (ProjectImportProvider)myList.getSelectedValue();
        if (provider != null) {
          mySequence.setType(provider.getId());
        }
      }
    });
    final String id = PropertiesComponent.getInstance().getValue(PREFFERED);
    if (id == null) {
      myList.setSelectedIndex(0);
    } else {
      for (ProjectImportProvider provider : providers) {
        if (Comparing.strEqual(provider.getId(), id)) {
          myList.setSelectedValue(provider, true);
          break;
        }
      }
    }
  }

  private static List<ProjectImportProvider> sorted(ProjectImportProvider[] providers) {
    List<ProjectImportProvider> result = new ArrayList<ProjectImportProvider>();
    Collections.addAll(result, providers);
    Collections.sort(result, new Comparator<ProjectImportProvider>() {
      public int compare(ProjectImportProvider l, ProjectImportProvider r) {
        return l.getName().compareToIgnoreCase(r.getName());
      }
    });
    return result;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  public void updateDataModel() {
    final Object selectedValue = myList.getSelectedValue();
    if (selectedValue instanceof ProjectImportProvider) {
      mySequence.setType(((ProjectImportProvider)selectedValue).getId());
      final ProjectImportBuilder builder = ((ProjectImportProvider)selectedValue).getBuilder();
      getWizardContext().setProjectBuilder(builder);
      builder.setUpdate(getWizardContext().getProject() != null);
      PropertiesComponent.getInstance().setValue(PREFFERED, ((ProjectImportProvider)selectedValue).getId());
    }
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.import";
  }
}
