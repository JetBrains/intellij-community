/*
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

package com.intellij.application.options.codeStyle;

import com.intellij.application.options.schemes.AbstractSchemesPanel;
import com.intellij.application.options.schemes.DefaultSchemeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CodeStyleSchemesPanel extends AbstractSchemesPanel<CodeStyleScheme> {
  
  private final CodeStyleSchemesModel myModel;
  
  private boolean myIsReset = false;
  private Font myDefaultComboFont;
  private Font myBoldComboFont;

  public CodeStyleSchemesPanel(CodeStyleSchemesModel model) {
    myModel = model;
  }

  private void onCombo() {
    CodeStyleScheme selected = getSelectedScheme();
    if (selected != null) {
      if (myModel.isProjectScheme(selected)) {
        myModel.setUsePerProjectSettings(true);
      }
      else {
        myModel.selectScheme(selected, this);
        myModel.setUsePerProjectSettings(false);
      }
    }
  }

  @Nullable
  private CodeStyleScheme getSelectedScheme() {
    Object selected = getSchemesCombo().getSelectedItem();
    if (selected instanceof CodeStyleScheme) {
      return (CodeStyleScheme)selected;
    }
    return null;
  }

  public void resetSchemesCombo() {
    myIsReset = true;
    try {
      List<CodeStyleScheme> schemes = new ArrayList<>();
      schemes.addAll(myModel.getAllSortedSchemes());
      DefaultComboBoxModel model = new DefaultComboBoxModel(schemes.toArray());
      getSchemesCombo().setModel(model);
      if (myModel.isUsePerProjectSettings()) {
        getSchemesCombo().setSelectedItem(myModel.getProjectScheme());
      }
      else {
        getSchemesCombo().setSelectedItem(myModel.getSelectedGlobalScheme());
      }
    }
    finally {
      myIsReset = false;
    }
  }

  public void onSelectedSchemeChanged() {
    myIsReset = true;
    try {
      if (myModel.isUsePerProjectSettings()) {
        getSchemesCombo().setSelectedItem(myModel.getProjectScheme());
      }
      else {
        getSchemesCombo().setSelectedItem(myModel.getSelectedGlobalScheme());
      }
    }
    finally {
      myIsReset = false;
    }
  }

  public void usePerProjectSettingsOptionChanged() {
    if (myModel.isProjectScheme(myModel.getSelectedScheme())) {
      getSchemesCombo().setSelectedItem(myModel.getProjectScheme());
    }
    else {
      getSchemesCombo().setSelectedItem(myModel.getSelectedScheme());
    }
  }

  @Override
  protected ComboBox createSchemesCombo() {
    ComboBox schemesCombo = new ComboBox();
    myDefaultComboFont = schemesCombo.getFont();
    myBoldComboFont = myDefaultComboFont.deriveFont(Font.BOLD);
    schemesCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (!myIsReset) {
          ApplicationManager.getApplication().invokeLater(() -> onCombo());
        }
      }
    });
    schemesCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(final JList list, final Object value, final int index, final boolean selected, final boolean hasFocus) {
        Font font = myDefaultComboFont;
        if (value instanceof CodeStyleScheme) {
          CodeStyleScheme scheme = (CodeStyleScheme)value;
          if (scheme.isDefault() || myModel.isProjectScheme(scheme)) {
            font = myBoldComboFont;
          }
        }
        setFont(font);
      }
    });
    return schemesCombo;
  }

  @Override
  protected DefaultSchemeActions<CodeStyleScheme> createSchemeActions() {
    return
      new CodeStyleSchemesActions() {
        @NotNull
        @Override
        protected JComponent getParentComponent() {
          return getToolbarPanel();
        }

        @Override
        protected CodeStyleSchemesModel getSchemesModel() {
          return myModel;
        }

        @Nullable
        @Override
        protected CodeStyleScheme getCurrentScheme() {
          return getSelectedScheme();
        }
      };
  }
}
