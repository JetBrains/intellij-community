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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryFileChooser;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.List;
import java.util.*;

/**
* @author nik
*/
class ChooseModuleLibrariesDialog extends LibraryFileChooser implements ClasspathElementChooserDialog<Library> {
  private Pair<String, VirtualFile[]> myLastChosen;
  private final LibraryTable myLibraryTable;
  @Nullable private final VirtualFile myFileToSelect;

  public ChooseModuleLibrariesDialog(Component parent, final LibraryTable libraryTable, final VirtualFile fileToSelect) {
    super(createFileChooserDescriptor(parent), parent, false, null);
    myLibraryTable = libraryTable;
    myFileToSelect = fileToSelect;
  }

  private static FileChooserDescriptor createFileChooserDescriptor(Component parent) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, false, true);
    final Module contextModule = LangDataKeys.MODULE_CONTEXT.getData(DataManager.getInstance().getDataContext(parent));
    descriptor.putUserData(LangDataKeys.MODULE_CONTEXT, contextModule);
    return descriptor;
  }

  public java.util.List<Library> getChosenElements() {
    if (myLastChosen == null) {
      return Collections.emptyList();
    }
    final VirtualFile[] files = filterAlreadyAdded(myLastChosen.getSecond());
    if (files.length == 0) {
      return Collections.emptyList();
    }
    final LibraryTable.ModifiableModel modifiableModel = myLibraryTable.getModifiableModel();
    final java.util.List<Library> addedLibraries = new ArrayList<Library>(files.length);
    for (VirtualFile file : files) {
      final Library library = modifiableModel.createLibrary(null);
      final Library.ModifiableModel libModel = library.getModifiableModel();
      libModel.addRoot(file, OrderRootType.CLASSES);
      libModel.commit();
      addedLibraries.add(library);
    }
    return addedLibraries;
  }

  private VirtualFile[] filterAlreadyAdded(final VirtualFile[] files) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final Set<VirtualFile> chosenFilesSet = new HashSet<VirtualFile>(Arrays.asList(files));
    final Set<VirtualFile> alreadyAdded = new HashSet<VirtualFile>();
    final Library[] libraries = myLibraryTable.getLibraries();
    for (Library library : libraries) {
      ContainerUtil.addAll(alreadyAdded, library.getFiles(OrderRootType.CLASSES));
    }
    chosenFilesSet.removeAll(alreadyAdded);
    return VfsUtil.toVirtualFileArray(chosenFilesSet);
  }

  public void doChoose() {
    myLastChosen = chooseNameAndFiles(myFileToSelect);
  }
}
