/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.EntryPointsManagerImpl;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspectionBase;
import com.intellij.ui.components.JBTabbedPane;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class UnusedDeclarationInspection extends UnusedDeclarationInspectionBase {
  @TestOnly
  public UnusedDeclarationInspection(boolean enabledInEditor) {
    super(enabledInEditor);
  }

  public UnusedDeclarationInspection() {
  }

  @Override
  protected UnusedSymbolLocalInspectionBase createUnusedSymbolLocalInspection() {
    return new UnusedSymbolLocalInspection();
  }

  @Override
  public JComponent createOptionsPanel() {
    JTabbedPane tabs = new JBTabbedPane(SwingConstants.TOP);
    tabs.add("Entry points", new OptionsPanel());
    tabs.add("On the fly editor settings", myLocalInspectionBase.createOptionsPanel());
    return tabs;
  }

  private class OptionsPanel extends JPanel {
    private final JCheckBox myMainsCheckbox;
    private final JCheckBox myAppletToEntries;
    private final JCheckBox myServletToEntries;
    private final JCheckBox myNonJavaCheckbox;

    private OptionsPanel() {
      super(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints();
      gc.weightx = 1;
      gc.weighty = 0;
      gc.insets = new Insets(0, 20, 2, 0);
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.anchor = GridBagConstraints.NORTHWEST;

      myMainsCheckbox = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.main"));
      myMainsCheckbox.setSelected(ADD_MAINS_TO_ENTRIES);
      myMainsCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_MAINS_TO_ENTRIES = myMainsCheckbox.isSelected();
        }
      });

      gc.gridy = 0;
      add(myMainsCheckbox, gc);

      myAppletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.applet"));
      myAppletToEntries.setSelected(ADD_APPLET_TO_ENTRIES);
      myAppletToEntries.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_APPLET_TO_ENTRIES = myAppletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myAppletToEntries, gc);

      myServletToEntries = new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.servlet"));
      myServletToEntries.setSelected(ADD_SERVLET_TO_ENTRIES);
      myServletToEntries.addActionListener(new ActionListener(){
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_SERVLET_TO_ENTRIES = myServletToEntries.isSelected();
        }
      });
      gc.gridy++;
      add(myServletToEntries, gc);

      for (final EntryPoint extension : myExtensions) {
        if (extension.showUI()) {
          final JCheckBox extCheckbox = new JCheckBox(extension.getDisplayName());
          extCheckbox.setSelected(extension.isSelected());
          extCheckbox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              extension.setSelected(extCheckbox.isSelected());
            }
          });
          gc.gridy++;
          add(extCheckbox, gc);
        }
      }

      myNonJavaCheckbox =
      new JCheckBox(InspectionsBundle.message("inspection.dead.code.option.external"));
      myNonJavaCheckbox.setSelected(ADD_NONJAVA_TO_ENTRIES);
      myNonJavaCheckbox.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          ADD_NONJAVA_TO_ENTRIES = myNonJavaCheckbox.isSelected();
        }
      });

      gc.gridy++;
      add(myNonJavaCheckbox, gc);

      JButton configureAnnotations = EntryPointsManagerImpl.createConfigureAnnotationsButton();
      gc.fill = GridBagConstraints.NONE;
      gc.gridy++;
      gc.insets.top = 10;
      gc.weighty = 1;

      add(configureAnnotations, gc);
    }

  }
}
