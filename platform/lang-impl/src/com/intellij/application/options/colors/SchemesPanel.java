/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.application.options.colors;

import com.intellij.application.options.SkipSelfSearchComponent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ManageSchemesComboAction;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SchemesPanel extends JPanel implements SkipSelfSearchComponent {
  private final ColorAndFontOptions myOptions;

  private ComboBox<MySchemeItem> mySchemeComboBox;
  
  private JLabel myHintLabel;

  private final EventDispatcher<ColorAndFontSettingsListener> myDispatcher = EventDispatcher.create(ColorAndFontSettingsListener.class);

  public SchemesPanel(ColorAndFontOptions options) {
    super(new BorderLayout());
    myOptions = options;

    JPanel schemesGroup = new JPanel(new BorderLayout());

    JPanel panel = new JPanel(new BorderLayout());
    schemesGroup.add(createSchemePanel(), BorderLayout.NORTH);
    schemesGroup.add(panel, BorderLayout.CENTER);
    add(schemesGroup, BorderLayout.CENTER);

    mySchemeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        String selectedName = getSelectedSchemeName();
        if (selectedName != null) {
          EditorColorsScheme selected = myOptions.selectScheme(selectedName);
          myHintLabel.setVisible(ColorAndFontOptions.isReadOnly(selected));
          if (areSchemesLoaded()) {
            myDispatcher.getMulticaster().schemeChanged(SchemesPanel.this);
          }
        }
      }
    });
  }

  private boolean myListLoaded = false;

  public boolean areSchemesLoaded() {
    return myListLoaded;
  }

  private JPanel createSchemePanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    int gridx = 0;

    panel.add(new JLabel(ApplicationBundle.message("editbox.scheme.name")),
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 5),
                                     0, 0));

    mySchemeComboBox = new ComboBox<>();
    panel.add(mySchemeComboBox,
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new JBInsets(0, 0, 5, 10),
                                     0, 0));
    ManageSchemesComboAction schemesComboAction = new ManageSchemesComboAction(new ColorSchemeActions(this, myOptions) {
      @Nullable
      @Override
      protected EditorColorsScheme getCurrentScheme() {
        return myOptions.getScheme(getSelectedSchemeName());
      }
    });
    JButton manageButton = schemesComboAction.createCombo();
    panel.add(manageButton,
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.VERTICAL, new JBInsets(0, 0, 5, 5),
                                     0, 0));
    
    myHintLabel = new JLabel(ApplicationBundle.message("hint.readonly.scheme.cannot.be.modified"));
    myHintLabel.setEnabled(false);
    panel.add(myHintLabel,
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 5), 0,
                                     0));

    for (final ImportHandler importHandler : Extensions.getExtensions(ImportHandler.EP_NAME)) {
      final JButton button = new JButton(importHandler.getTitle());
      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          importHandler.performImport(button, scheme -> {
            if (scheme != null) myOptions.addImportedScheme(scheme);
          });
        }
      });
      panel.add(button,
                new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 5), 0,
                                       0));
    }
    panel.add(Box.createHorizontalGlue(),
              new GridBagConstraints(gridx+1, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new JBInsets(0, 0, 0, 0), 0,
                                     0));

    return panel;
  }

  @Deprecated
  public boolean updateDescription(boolean modified) {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    if (modified && ColorAndFontOptions.isReadOnly(scheme)) {
      return false;
    }

    return true;
  }

  public void resetSchemesCombo(final Object source) {
    if (this != source) {
      setListLoaded(false);

      EditorColorsScheme selectedSchemeBackup = myOptions.getSelectedScheme();
      mySchemeComboBox.removeAllItems();

      String[] schemeNames = myOptions.getSchemeNames();
      MySchemeItem itemToSelect = null;
      for (String schemeName : schemeNames) {
        EditorColorsScheme scheme = myOptions.getScheme(schemeName); 
        MySchemeItem item = new MySchemeItem(scheme);
        if (scheme == selectedSchemeBackup) itemToSelect = item;
        mySchemeComboBox.addItem(item);
      }

      mySchemeComboBox.setSelectedItem(itemToSelect);
      setListLoaded(true);

      myDispatcher.getMulticaster().schemeChanged(this);
    }
  }
  
  @Nullable
  private String getSelectedSchemeName() {
    return mySchemeComboBox.getSelectedIndex() != -1 ? ((MySchemeItem)mySchemeComboBox.getSelectedItem()).getSchemeName() : null;
  }

  private void setListLoaded(final boolean b) {
    myListLoaded = b;
  }

  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }
  
  private final static class MySchemeItem {
    private EditorColorsScheme myScheme;

    public MySchemeItem(EditorColorsScheme scheme) {
      myScheme = scheme;
    }
    
    public String getSchemeName() {
      return myScheme.getName();
    }

    @Override
    public String toString() {
      return AbstractColorsScheme.getDisplayName(myScheme);
    }
  }
  
}
