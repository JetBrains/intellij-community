// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.SchemeFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;

public class ImportSchemeChooserDialog extends DialogWrapper {
  private JPanel contentPane;
  private JBList<String> mySchemeList;
  private JTextField myTargetNameField;
  private JCheckBox myUseCurrentScheme;
  private String mySelectedName;
  private final List<String> myNames = new ArrayList<>();

  public ImportSchemeChooserDialog(@NotNull Project project, @NlsContexts.Label String @NotNull [] schemeNames, @Nullable String currentScheme) {
    super(project, false);
    if (schemeNames.length > 0) {
      myNames.addAll(Arrays.asList(schemeNames));
    }
    else {
      myNames.add(null);
    }
    mySchemeList.setModel(new DefaultListModel<>() {
      @Override
      public int getSize() {
        return myNames.size();
      }

      @Override
      public String getElementAt(int index) {
        return myNames.get(index);
      }
    });
    mySchemeList.setCellRenderer(SimpleListCellRenderer.create(
      (JBLabel label, @NlsContexts.Label String value, int index) -> label.setText(value == null ? '<' + ApplicationBundle.message("code.style.scheme.import.unnamed") + '>' : value)));
    mySchemeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    mySchemeList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int index = mySchemeList.getSelectedIndex();
        if (index >= 0) {
          mySelectedName = myNames.get(index);
          if (!myUseCurrentScheme.isSelected() && mySelectedName != null) {
            myTargetNameField.setText(mySelectedName);
          }
        }
      }
    });
    myUseCurrentScheme.setEnabled(currentScheme != null);
    myUseCurrentScheme.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myUseCurrentScheme.isSelected()) {
          myTargetNameField.setEnabled(false);
          if (currentScheme != null) {
            myTargetNameField.setText(currentScheme);
          }
        }
        else {
          myTargetNameField.setEnabled(true);
          if (mySelectedName != null) myTargetNameField.setText(mySelectedName);
        }
      }
    });
    mySchemeList.getSelectionModel().setSelectionInterval(0,0);
    init();
    setTitle(ApplicationBundle.message("title.import.scheme.chooser"));
  }

  public String getSelectedName() {
    return mySelectedName;
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return contentPane;
  }

  public boolean isUseCurrentScheme() {
    return myUseCurrentScheme.isSelected();
  }

  public @Nullable String getTargetName() {
    String name = myTargetNameField.getText();
    return name != null && !name.trim().isEmpty() ? name : null;
  }

  public static @Nullable Pair<String, CodeStyleScheme> selectOrCreateTargetScheme(@NotNull Project project,
                                                                                   @NotNull CodeStyleScheme currentScheme,
                                                                                   @NotNull SchemeFactory<? extends CodeStyleScheme> schemeFactory,
                                                                                   @NlsContexts.Label String @NotNull... schemeNames) {
    ImportSchemeChooserDialog dialog = new ImportSchemeChooserDialog(project, schemeNames, !currentScheme.isDefault() ? currentScheme.getName() : null);
    if (dialog.showAndGet()) {
      return pair(
        dialog.getSelectedName(),
        dialog.isUseCurrentScheme() && (!currentScheme.isDefault()) ? currentScheme : schemeFactory.createNewScheme(dialog.getTargetName()));
    }
    return null;
  }
}
