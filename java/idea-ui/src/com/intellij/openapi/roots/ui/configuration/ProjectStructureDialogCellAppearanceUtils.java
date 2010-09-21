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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.util.CellAppearance;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;

import javax.swing.*;

/**
 * @author nik
 */
//todo[nik] extract service from OrderEntryCellAppearanceUtils and use this class as its implementation for idea-ui
public class ProjectStructureDialogCellAppearanceUtils {

  private ProjectStructureDialogCellAppearanceUtils() {
  }

  public static CellAppearance forOrderEntry(OrderEntry orderEntry, StructureConfigurableContext context, boolean selected) {
    if (orderEntry instanceof LibraryOrderEntry) {
      final Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
      if (orderEntry.isValid()) {
        return forLibrary(library, context);
      }
    }
    return OrderEntryCellAppearanceUtils.forOrderEntry(orderEntry, selected);
  }

  public static CellAppearance forLibrary(Library library, StructureConfigurableContext context) {
    final Icon icon = LibraryPresentationManager.getInstance().getCustomIcon(library, context);
    if (icon != null) {
      final String name = library.getName();
      if (name != null) {
        return SimpleTextCellAppearance.normal(name, icon);
      }
    }
    return OrderEntryCellAppearanceUtils.forLibrary(library);
  }

}
