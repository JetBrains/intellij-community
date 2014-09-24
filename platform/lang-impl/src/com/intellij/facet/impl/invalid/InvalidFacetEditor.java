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

import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class InvalidFacetEditor extends FacetEditorTab {
  private final String myErrorMessage;
  private JPanel myMainPanel;
  private MultiLineLabel myDescriptionLabel;
  private JCheckBox myIgnoreCheckBox;
  private JLabel myIconLabel;
  private final InvalidFacetManager myInvalidFacetManager;
  private final InvalidFacet myFacet;

  public InvalidFacetEditor(FacetEditorContext context, String errorMessage) {
    myErrorMessage = errorMessage;
    myFacet = (InvalidFacet)context.getFacet();
    myInvalidFacetManager = InvalidFacetManager.getInstance(context.getProject());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "";
  }

  public JCheckBox getIgnoreCheckBox() {
    return myIgnoreCheckBox;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    myIconLabel.setIcon(AllIcons.RunConfigurations.ConfigurationWarning);
    myDescriptionLabel.setText(myErrorMessage);
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return myIgnoreCheckBox.isSelected() != myInvalidFacetManager.isIgnored(myFacet);
  }

  @Override
  public void reset() {
    myIgnoreCheckBox.setSelected(myInvalidFacetManager.isIgnored(myFacet));
  }

  @Override
  public void apply() {
    myInvalidFacetManager.setIgnored(myFacet, myIgnoreCheckBox.isSelected());

  }

  @Override
  public void disposeUIResources() {
  }
}
