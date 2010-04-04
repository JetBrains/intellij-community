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
 * Date: 08-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.util.newProjectWizard.modes.CreateFromScratchMode;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromSourcesMode;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class ProjectCreateModeStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");


  private final JPanel myWholePanel;

  private WizardMode myMode;
  private final List<WizardMode> myModes = new ArrayList<WizardMode>();
  private final WizardContext myWizardContext;

  public ProjectCreateModeStep(final String defaultPath, final WizardContext wizardContext) {
    final StringBuffer buf = new StringBuffer();
    for (WizardMode mode : Extensions.getExtensions(WizardMode.MODES)) {
      if (mode.isAvailable(wizardContext)) {
        myModes.add(mode);
        if (defaultPath != null) {
          if (mode instanceof CreateFromSourcesMode) {
            myMode = mode;
          }
        } else if (mode instanceof CreateFromScratchMode) {
          myMode = mode;
        }
      }
      final String footnote = mode.getFootnote(wizardContext);
      if (footnote != null) {
        if (buf.length() > 0) buf.append("<br>");
        buf.append(footnote);
      }
    }
    myWizardContext = wizardContext;
    myWholePanel = new JPanel(new GridBagLayout());
    myWholePanel.setBorder(BorderFactory.createEtchedBorder());

    final Insets insets = new Insets(0, 0, 0, 5);
    GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST,
                                                   GridBagConstraints.HORIZONTAL, insets, 0, 0);
    final ButtonGroup group = new ButtonGroup();
    for (final WizardMode mode : myModes) {
      insets.top = 15;
      insets.left = 5;
      final JRadioButton rb = new JRadioButton(mode.getDisplayName(wizardContext), mode == myMode);
      rb.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      rb.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          myMode.onChosen(false);
          myMode = mode;
          myMode.onChosen(true);
          update();
        }
      });
      rb.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
            wizardContext.requestNextStep();
          }
        }
      });

      myWholePanel.add(rb, gc);
      group.add(rb);
      insets.top = 5;
      insets.left = 20;
      final JLabel description = new JLabel(mode.getDescription(wizardContext));
      myWholePanel.add(description, gc);
      final JComponent settings = mode.getAdditionalSettings();
      if (settings != null) {
        myWholePanel.add(settings, gc);
      }
    }
    gc.weighty = 1;
    gc.fill = GridBagConstraints.BOTH;
    myWholePanel.add(Box.createVerticalBox(), gc);
    final JLabel note = new JLabel( "<html>" + buf.toString() + "</html>", IconLoader.getIcon("/nodes/warningIntroduction.png"), SwingUtilities.LEFT);
    note.setVisible(buf.length() > 0);
    gc.weighty = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.insets.bottom = 5;
    myWholePanel.add(note, gc);
  }

  public JComponent getComponent() {
    return myWholePanel;
  }

  public void updateDataModel() {
    myWizardContext.setProjectBuilder(myMode.getModuleBuilder());
  }

  public Icon getIcon() {
    return myWizardContext.getProject() == null ? NEW_PROJECT_ICON : ICON;
  }

  public WizardMode getMode() {
    return myMode;
  }

  public void disposeUIResources() {
    super.disposeUIResources();
    for (WizardMode mode : myModes) {
      Disposer.dispose(mode);
    }
  }

  protected void update() {
  }

  @Override
  public boolean validate() throws ConfigurationException {
    return super.validate() && myMode.validate();
  }

  @NonNls
  public String getHelpId() {
    return myWizardContext.getProject() == null ? "reference.dialogs.new.project" : "reference.dialogs.new.module";
  }

  public List<WizardMode> getModes() {
    return myModes;
  }
}
