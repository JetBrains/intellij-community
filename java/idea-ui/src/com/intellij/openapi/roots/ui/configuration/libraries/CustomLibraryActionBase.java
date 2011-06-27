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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public abstract class CustomLibraryActionBase extends DumbAwareAction {
  protected final CustomLibraryCreator myCreator;
  protected final StructureConfigurableContext myContext;
  protected final ModuleStructureConfigurable myModuleStructureConfigurable;
  protected Module myModule;

  protected CustomLibraryActionBase(String text, String description, Icon icon, StructureConfigurableContext context,
                                    ModuleStructureConfigurable moduleStructureConfigurable, CustomLibraryCreator creator, Module module) {
    super(text, description, icon);
    myContext = context;
    myModuleStructureConfigurable = moduleStructureConfigurable;
    myCreator = creator;
    myModule = module;
  }

  protected boolean askAndRemoveDuplicatedLibraryEntry(@NotNull CustomLibraryDescription description, @NotNull ModifiableRootModel rootModel) {
    List<OrderEntry> existingEntries = new ArrayList<OrderEntry>();
    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (!(entry instanceof LibraryOrderEntry)) continue;
      final Library library = ((LibraryOrderEntry)entry).getLibrary();
      if (library == null) continue;

      final VirtualFile[] files = myContext.getLibraryFiles(library, OrderRootType.CLASSES);
      if (description.getSuitableLibraryFilter().isSuitableLibrary(Arrays.asList(files), ((LibraryEx)library).getType())) {
        existingEntries.add(entry);
      }
    }

    if (!existingEntries.isEmpty()) {
      String message;
      if (existingEntries.size() > 1) {
        message = "There are already " + existingEntries.size() + " " + myCreator.getDisplayName() + " libraries.\n Do you want to replace they?";
      }
      else {
        final String name = existingEntries.get(0).getPresentableName();
        message = "There is already a " + myCreator.getDisplayName() + " library '" + name + "'.\n Do you want to replace it?";
      }
      final int result = Messages.showDialog(rootModel.getProject(), message, "Library Already Exists", new String[]{"&Replace", "&Add", "&Cancel"}, 0, null);
      if (result == 0) {
        for (OrderEntry entry : existingEntries) {
          rootModel.removeOrderEntry(entry);
        }
      }
      else if (result != 1) {
        return false;
      }
    }
    return true;
  }
}
