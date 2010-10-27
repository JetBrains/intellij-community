/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.facet.impl.invalid;

import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author nik
 */
public class InvalidFacetEditor extends FacetEditorTab {
  private final String myErrorMessage;
  private JPanel myMainPanel;
  private JLabel myErrorLabel;

  public InvalidFacetEditor(String errorMessage) {
    myErrorMessage = errorMessage;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "";
  }

  @Override
  public JComponent createComponent() {
    myErrorLabel.setText(myErrorMessage);
    myErrorLabel.setIcon(IconLoader.getIcon("/runConfigurations/configurationWarning.png"));
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {
  }
}
