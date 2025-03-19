// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors.pluginExport;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class PluginInfoDialog extends DialogWrapper {
  private final PluginExportData myExportData;
  private PluginInfoForm myForm;

  PluginInfoDialog(@NotNull Component parent, @NotNull PluginExportData exportData) {
    super(parent, false);
    myExportData = exportData;
    setTitle(LangBundle.message("dialog.title.create.color.scheme.plug.in"));
    init();
  }

  @Override
  protected void init() {
    super.init();
    myForm.init(myExportData);
  }

  public void apply() {
    myForm.apply(myExportData);
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    myForm = new PluginInfoForm();
    return myForm.getTopPanel();
  }

}
