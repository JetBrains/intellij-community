/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class ChooseLibrariesDialog extends DialogWrapper {
  private final LibraryElementChooser myChooser;

  public ChooseLibrariesDialog(final Component parent, final List<Library> libraries) {
    super(parent, true);
    setTitle(ProjectBundle.message("dialog.title.select.libraries"));
    setModal(true);
    myChooser = new LibraryElementChooser(libraries);
    init();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myChooser;
  }

  public void markElements(final Collection<Library> elements) {
    myChooser.markElements(elements);
  }

  public List<Library> getMarkedLibraries() {
    return myChooser.getMarkedElements();
  }

  static class LibraryElementChooser extends ElementsChooser<Library> {
    LibraryElementChooser(final List<Library> elements) {
      super(elements, false);
    }

    protected Icon getItemIcon(final Library value) {
      return Icons.LIBRARY_ICON;
    }

    protected String getItemText(@NotNull final Library value) {
      return OrderEntryCellAppearanceUtils.forLibrary(value).getText();
    }
  }
}
