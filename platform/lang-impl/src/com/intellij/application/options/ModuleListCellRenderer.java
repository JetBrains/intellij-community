// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author yole
*/
public class ModuleListCellRenderer extends SimpleListCellRenderer<Module> {
  private final @NlsContexts.ListItem String myEmptySelectionText;

  public ModuleListCellRenderer() {
    this(LangBundle.message("list.item.none"));
  }

  public ModuleListCellRenderer(@NotNull @NlsContexts.ListItem String emptySelectionText) {
    myEmptySelectionText = emptySelectionText;
  }

  @Override
  public void customize(@NotNull JList<? extends Module> list, Module value, int index, boolean selected, boolean hasFocus) {
    if (value == null) {
      setText(myEmptySelectionText);
    }
    else {
      setIcon(ModuleType.get(value).getIcon());
      setText(value.getName());
    }
  }
}
