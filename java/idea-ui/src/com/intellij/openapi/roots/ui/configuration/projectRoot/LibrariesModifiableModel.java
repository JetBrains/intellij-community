// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditorListener;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class LibrariesModifiableModel implements LibraryTable.ModifiableModel {
  //todo[nik] remove LibraryImpl#equals method instead of using identity maps
  private final Map<Library, ExistingLibraryEditor> myLibrary2EditorMap = new IdentityHashMap<>();
  private final Set<Library> myRemovedLibraries = Collections.newSetFromMap(new IdentityHashMap<>());

  private LibraryTable.ModifiableModel myLibrariesModifiableModel;
  private final Project myProject;
  private final LibraryTable myTable;
  private final LibraryEditorListener myLibraryEditorListener;

  public LibrariesModifiableModel(final LibraryTable table, final Project project, LibraryEditorListener libraryEditorListener) {
    myProject = project;
    myTable = table;
    myLibraryEditorListener = libraryEditorListener;
  }

  @NotNull
  @Override
  public Library createLibrary(String name) {
    return createLibrary(name, null);
  }

  @NotNull
  @Override
  public Library createLibrary(String name, @Nullable PersistentLibraryKind<?> type) {
    return createLibrary(name, type, null);
  }

  @NotNull
  @Override
  public Library createLibrary(String name, @Nullable PersistentLibraryKind<?> type, @Nullable ProjectModelExternalSource externalSource) {
    final Library library = getLibrariesModifiableModel().createLibrary(name, type, externalSource);
    final BaseLibrariesConfigurable configurable = ProjectStructureConfigurable.getInstance(myProject).getConfigurableFor(library);
    configurable.createLibraryNode(library);
    return library;
  }

  @Override
  public void removeLibrary(@NotNull Library library) {
    if (getLibrariesModifiableModel().getLibraryByName(library.getName()) == null) return;

    removeLibraryEditor(library);
    final Library existingLibrary = myTable.getLibraryByName(library.getName());
    getLibrariesModifiableModel().removeLibrary(library);

    final BaseLibrariesConfigurable configurable = ProjectStructureConfigurable.getInstance(myProject).getConfigurableFor(library);
    configurable.removeLibraryNode(library);

    if (existingLibrary == library) {
      myRemovedLibraries.add(library);
    }
  }

  @Override
  public void commit() {
    //do nothing  - do deffered commit
  }

  @Override
  @NotNull
  public Iterator<Library> getLibraryIterator() {
    return getLibrariesModifiableModel().getLibraryIterator();
  }

  @Override
  public Library getLibraryByName(@NotNull String name) {
    return getLibrariesModifiableModel().getLibraryByName(name);
  }

  @Override
  public Library @NotNull [] getLibraries() {
    return getLibrariesModifiableModel().getLibraries();
  }

  @Override
  public boolean isChanged() {
    for (LibraryEditor libraryEditor : myLibrary2EditorMap.values()) {
      if (libraryEditor.hasChanges()) return true;
    }
    return getLibrariesModifiableModel().isChanged();
  }

  public void deferredCommit(){
    final List<ExistingLibraryEditor> libraryEditors = new ArrayList<>(myLibrary2EditorMap.values());
    myLibrary2EditorMap.clear();
    for (ExistingLibraryEditor libraryEditor : libraryEditors) {
      libraryEditor.commit(); // TODO: is seems like commit will recreate the editor, but it should not
      Disposer.dispose(libraryEditor);
    }
    if (!libraryEditors.isEmpty() || !myRemovedLibraries.isEmpty() || myLibrariesModifiableModel != null && myLibrariesModifiableModel.isChanged()) {
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
    if (library instanceof LibraryEx) {
      final Library source = ((LibraryEx)library).getSource();
      if (source != null) {
        return getLibraryEditor(source);
      }
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
    final ExistingLibraryEditor libraryEditor = myLibrary2EditorMap.remove(library);
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

  @Override
  public void dispose() {
    if (myLibrariesModifiableModel != null) {
      Disposer.dispose(myLibrariesModifiableModel);
      myLibrariesModifiableModel = null;
    }
    disposeLibraryEditors();
  }

  private void disposeLibraryEditors() {
    for (ExistingLibraryEditor libraryEditor : myLibrary2EditorMap.values()) {
      Disposer.dispose(libraryEditor);
    }
    myLibrary2EditorMap.clear();
  }
}
