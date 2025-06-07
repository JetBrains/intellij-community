// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class ChooseAndAddAction<ItemType> extends ClasspathPanelAction {
  protected ChooseAndAddAction(ClasspathPanel classpathPanel) {
    super(classpathPanel);
  }

  @Override
  public void run() {
    final ClasspathElementChooser<ItemType> dialog = createChooser();
    if (dialog == null) {
      return;
    }
    final List<ItemType> chosen = dialog.chooseElements();
    if (chosen.isEmpty()) {
      return;
    }
    List<ClasspathTableItem<?>> toAdd = new ArrayList<>();
    for (ItemType item : chosen) {
      final ClasspathTableItem<?> tableItem = createTableItem(item);
      if (tableItem != null) {
        toAdd.add(tableItem);
      }
    }
    myClasspathPanel.addItems(toAdd);
  }

  protected abstract @Nullable ClasspathTableItem<?> createTableItem(final ItemType item);

  protected abstract @Nullable ClasspathElementChooser<ItemType> createChooser();
}
