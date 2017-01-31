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

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.AbstractSchemesPanel;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CodeStyleSchemesPanel extends AbstractSchemesPanel<CodeStyleScheme> {
  
  private final CodeStyleSchemesModel myModel;
  
  private boolean myIsReset = false;

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

  public void resetSchemesCombo() {
    myIsReset = true;
    try {
      List<CodeStyleScheme> schemes = new ArrayList<>();
      schemes.addAll(myModel.getAllSortedSchemes());
      resetSchemes(schemes);
      if (myModel.isUsePerProjectSettings()) {
        selectScheme(myModel.getProjectScheme());
      }
      else {
        selectScheme(myModel.getSelectedGlobalScheme());
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
        selectScheme(myModel.getProjectScheme());
      }
      else {
        selectScheme(myModel.getSelectedGlobalScheme());
      }
    }
    finally {
      myIsReset = false;
    }
  }

  public void usePerProjectSettingsOptionChanged() {
    if (myModel.isProjectScheme(myModel.getSelectedScheme())) {
      selectScheme(myModel.getProjectScheme());
    }
    else {
      selectScheme(myModel.getSelectedScheme());
    }
  }
  

  @Override
  protected AbstractSchemeActions<CodeStyleScheme> createSchemeActions() {
    return
      new CodeStyleSchemesActions(this) {

        @Override
        protected void onSchemeChanged(@Nullable CodeStyleScheme scheme) {
          if (!myIsReset) {
            ApplicationManager.getApplication().invokeLater(() -> onCombo());
          }
        }

        @Override
        protected void renameScheme(@NotNull CodeStyleScheme scheme, @NotNull String newName) {
          CodeStyleSchemeImpl newScheme = new CodeStyleSchemeImpl(newName, false, scheme);
          myModel.addScheme(newScheme, false);
          myModel.removeScheme(scheme);
          myModel.selectScheme(newScheme, null);
        }
      };
  }

  @NotNull
  @Override
  public SchemesModel<CodeStyleScheme> getModel() {
    return myModel;
  }

  @Override
  public boolean supportsProjectSchemes() {
    return true;
  }
}
