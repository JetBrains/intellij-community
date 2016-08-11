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
package com.intellij.openapi.roots.ui.configuration.classpath;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
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

  @Nullable
  protected abstract ClasspathTableItem<?> createTableItem(final ItemType item);

  @Nullable
  protected abstract ClasspathElementChooser<ItemType> createChooser();
}
