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

import com.intellij.application.options.ImportSourceChooserDialog;
import com.intellij.application.options.SaveSchemeDialog;
import com.intellij.application.options.SkipSelfSearchComponent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.options.SchemeImportUtil;
import com.intellij.openapi.options.SchemeImporter;
import com.intellij.openapi.options.SchemeImporterEP;
import com.intellij.openapi.project.DefaultProjectFactory;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class SchemesPanel extends JPanel implements SkipSelfSearchComponent {
  private final ColorAndFontOptions myOptions;

  private ComboBox<String> mySchemeComboBox;

  private JButton myDeleteButton;
  private JButton myImportButton;
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
        if (mySchemeComboBox.getSelectedIndex() != -1) {
          EditorColorsScheme selected = myOptions.selectScheme((String)mySchemeComboBox.getSelectedItem());
          final boolean readOnly = ColorAndFontOptions.isReadOnly(selected);
          myDeleteButton.setEnabled(!readOnly);
          myHintLabel.setVisible(readOnly);
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
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 10),
                                     0, 0));

    JButton saveAsButton = new JButton(ApplicationBundle.message("button.save.as"));
    saveAsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        showSaveAsDialog();
      }
    });
    panel.add(saveAsButton,
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 5),
                                     0, 0));

    myDeleteButton = new JButton(ApplicationBundle.message("button.delete"));
    myDeleteButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (mySchemeComboBox.getSelectedIndex() != -1) {
          myOptions.removeScheme((String)mySchemeComboBox.getSelectedItem());
        }
      }
    });
    panel.add(myDeleteButton,
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 5), 0,
                                     0));
    myImportButton = new JButton("Import...");
    myImportButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        chooseAndImport();
      }
    });
    panel.add(myImportButton,
              new GridBagConstraints(gridx++, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new JBInsets(0, 0, 5, 5), 0,
                                     0));
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

  private void showSaveAsDialog() {
    List<String> names = ContainerUtil.newArrayList(myOptions.getSchemeNames());
    String selectedName = myOptions.getSelectedScheme().getName();
    SaveSchemeDialog dialog = new SaveSchemeDialog(this, ApplicationBundle.message("title.save.color.scheme.as"), names, selectedName);
    if (dialog.showAndGet()) {
      myOptions.saveSchemeAs(dialog.getSchemeName());
    }
  }

  private void changeToScheme() {
    updateDescription(false);
  }

  public boolean updateDescription(boolean modified) {
    EditorColorsScheme scheme = myOptions.getSelectedScheme();

    if (modified && (ColorAndFontOptions.isReadOnly(scheme) || ColorSettingsUtil.isSharedScheme(scheme))) {
      return false;
    }

    return true;
  }

  public void resetSchemesCombo(final Object source) {
    if (this != source) {
      setListLoaded(false);

      String selectedSchemeBackup = myOptions.getSelectedScheme().getName();
      mySchemeComboBox.removeAllItems();

      String[] schemeNames = myOptions.getSchemeNames();
      for (String schemeName : schemeNames) {
        mySchemeComboBox.addItem(schemeName);
      }

      mySchemeComboBox.setSelectedItem(selectedSchemeBackup);
      setListLoaded(true);

      changeToScheme();

      myDispatcher.getMulticaster().schemeChanged(this);
    }
  }

  private void setListLoaded(final boolean b) {
    myListLoaded = b;
  }

  public void addListener(ColorAndFontSettingsListener listener) {
    myDispatcher.addListener(listener);
  }
  
  private void chooseAndImport() {
    ImportSourceChooserDialog<EditorColorsScheme> importSourceChooserDialog =
      new ImportSourceChooserDialog<>(this, EditorColorsScheme.class);
    if (importSourceChooserDialog.showAndGet()) {
      final String selectedImporterName = importSourceChooserDialog.getSelectedSourceName();
      if (selectedImporterName != null) {
        final SchemeImporter<EditorColorsScheme> importer = SchemeImporterEP.getImporter(selectedImporterName, EditorColorsScheme.class);
        if (importer != null) {
          VirtualFile importSource = SchemeImportUtil.selectImportSource(importer.getSourceExtensions(), this, null);
          if (importSource != null) {
            try {
              EditorColorsScheme imported =
                importer.importScheme(DefaultProjectFactory.getInstance().getDefaultProject(), importSource, myOptions.getSelectedScheme(),
                                      name -> {
                                        String newName = myOptions.getUniqueName(name);
                                        AbstractColorsScheme newScheme = new EditorColorsSchemeImpl(EmptyColorScheme.INSTANCE);
                                        newScheme.setName(newName);
                                        newScheme.setDefaultMetaInfo(EmptyColorScheme.INSTANCE);
                                        return newScheme;
                                      });
              if (imported != null) {
                myOptions.addImportedScheme(imported);
              }
              
            }
            catch (SchemeImportException e) {
              SchemeImportUtil.showStatus(myImportButton, "Import failed: " + e.getMessage(), MessageType.ERROR);
            }
          }
        }
      }
    }
  }
  
}
