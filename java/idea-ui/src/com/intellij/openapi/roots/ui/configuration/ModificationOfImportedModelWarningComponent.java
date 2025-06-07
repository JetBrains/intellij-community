// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ModificationOfImportedModelWarningComponent {
  private final JLabel myLabel;

  public ModificationOfImportedModelWarningComponent() {
    myLabel = new JLabel();
    hideWarning();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public void showWarning(@NotNull @Nls String elementDescription, @NotNull ProjectModelExternalSource externalSource) {
    myLabel.setVisible(true);
    myLabel.setBorder(JBUI.Borders.empty(5, 5));
    myLabel.setIcon(AllIcons.General.BalloonWarning);
    myLabel.setText(UIUtil.toHtml(getWarningText(elementDescription, externalSource)));
  }

  public static @NotNull @NlsContexts.Label String getWarningText(@NotNull @Nls String elementDescription, @NotNull ProjectModelExternalSource externalSource) {
    return JavaUiBundle.message("modification.imported.model.warning.label.text", elementDescription, externalSource.getDisplayName());
  }

  public void hideWarning() {
    myLabel.setVisible(false);
  }
}
