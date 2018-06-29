/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class ModificationOfImportedModelWarningComponent {
  private final JLabel myLabel;

  public ModificationOfImportedModelWarningComponent() {
    myLabel = new JLabel();
    hideWarning();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public void showWarning(@NotNull String elementDescription, @NotNull ProjectModelExternalSource externalSource) {
    myLabel.setVisible(true);
    myLabel.setBorder(JBUI.Borders.empty(5, 5));
    myLabel.setIcon(AllIcons.General.BalloonWarning);
    myLabel.setText(UIUtil.toHtml(getWarningText(elementDescription, externalSource)));
  }

  @NotNull
  public static String getWarningText(@NotNull String elementDescription, @NotNull ProjectModelExternalSource externalSource) {
    return elementDescription + " is imported from " + externalSource.getDisplayName() + ". Any changes made in its configuration may be lost after reimporting.";
  }

  public void hideWarning() {
    myLabel.setVisible(false);
  }
}
