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

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 * Date: 04-Jun-2006
 */

public class LibrariesModifiableModel implements LibraryTable.ModifiableModel {
  private final Map<Library, ExistingLibraryEditor> myLibrary2EditorMap = new HashMap<Library, ExistingLibraryEditor>();
  private final Set<Library> myRemovedLibraries = new HashSet<Library>();

  private LibraryTable.ModifiableModel myLibrariesModifiableModel;
  private final Project myProject;
  private final LibraryTable myTable;
  private final LibraryEditorListener myLibraryEditorListener;

  public LibrariesModifiableModel(final LibraryTable table, final Project project, LibraryEditorListener libraryEditorListener) {
    myProject = project;
    myTable = table;
    myLibraryEditorListener = libraryEditorListener;
  }

  public Library createLibrary(String name) {
    final Library library = getLibrariesModifiableModel().createLibrary(name);
    //createLibraryEditor(library);
    final BaseLibrariesConfigurable configurable = ProjectStructureConfigurable.getInstance(myProject).getConfigurableFor(library);
    configurable.createLibraryNode(library);
    return library;
  }

  public void removeLibrary(@NotNull Library library) {
    if (getLibrariesModifiableModel().getLibraryByName(library.getName()) == null) return;

    removeLibraryEditor(library);
    final Library existingLibrary = myTable.getLibraryByName(library.getName());
    getLibrariesModifiableModel().removeLibrary(library);
    if (existingLibrary == library) {
      myRemovedLibraries.add(library);
    } else {
      // dispose uncommitted library
      Disposer.dispose(library);
    }
  }

  public void commit() {
    //do nothing  - do deffered commit
  }

  @NotNull
  public Iterator<Library> getLibraryIterator() {
    return getLibrariesModifiableModel().getLibraryIterator();
  }

  public Library getLibraryByName(@NotNull String name) {
    return getLibrariesModifiableModel().getLibraryByName(name);
  }

  @NotNull
  public Library[] getLibraries() {
    return getLibrariesModifiableModel().getLibraries();
  }

  public boolean isChanged() {
    for (LibraryEditor libraryEditor : myLibrary2EditorMap.values()) {
      if (libraryEditor.hasChanges()) return true;
    }
    return getLibrariesModifiableModel().isChanged();
  }

  public void deferredCommit(){
    final List<ExistingLibraryEditor> libraryEditors = new ArrayList<ExistingLibraryEditor>(myLibrary2EditorMap.values());
    myLibrary2EditorMap.clear();
    for (ExistingLibraryEditor libraryEditor : libraryEditors) {
      libraryEditor.commit(); // TODO: is seems like commit will recreate the editor, but it should not
      Disposer.dispose(libraryEditor);
    }
    if (!libraryEditors.isEmpty() || !myRemovedLibraries.isEmpty()) {
      getLibrariesModifiableModel().commit();
      myLibrariesModifiableModel = null;
    }
    myRemovedLibraries.clear();
  }

  public boolean wasLibraryRemoved(Library library){
    return myRemovedLibraries.contains(library);
  }

  public boolean hasLibraryEditor(Library library){
    return myLibrary2EditorMap.containsKey(library);
  }

  public ExistingLibraryEditor getLibraryEditor(Library library){
    final Library source = ((LibraryImpl)library).getSource();
    if (source != null) {
      return getLibraryEditor(source);
    }
    ExistingLibraryEditor libraryEditor = myLibrary2EditorMap.get(library);
    if (libraryEditor == null){
      libraryEditor = createLibraryEditor(library);
    }
    return libraryEditor;
  }

  private ExistingLibraryEditor createLibraryEditor(final Library library) {
    final ExistingLibraryEditor libraryEditor = new ExistingLibraryEditor(library, myLibraryEditorListener);
    myLibrary2EditorMap.put(library, libraryEditor);
    return libraryEditor;
  }

  private void removeLibraryEditor(final Library library) {
    final LibraryEditor libraryEditor = myLibrary2EditorMap.remove(library);
    if (libraryEditor != null) {
      Disposer.dispose(libraryEditor);
    }
  }

  public Library.ModifiableModel getLibraryModifiableModel(final Library library) {
    return getLibraryEditor(library).getModel();
  }

  private LibraryTable.ModifiableModel getLibrariesModifiableModel() {
    if (myLibrariesModifiableModel == null) {
      myLibrariesModifiableModel = myTable.getModifiableModel();
    }

    return myLibrariesModifiableModel;
  }

  public void disposeUncommittedLibraries() {
    for (final Library library : new ArrayList<Library>(myLibrary2EditorMap.keySet())) {
      final Library existingLibrary = myTable.getLibraryByName(library.getName());
      if (existingLibrary != library) {
        Disposer.dispose(library);
      }

      final LibraryEditor libraryEditor = myLibrary2EditorMap.get(library);
      if (libraryEditor != null) {
        Disposer.dispose(libraryEditor);
      }
    }

    myLibrary2EditorMap.clear();
  }
}
