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
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryRootsComponent;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 02-Jun-2006
 */
public class LibraryConfigurable extends ProjectStructureElementConfigurable<Library> {
  private LibraryRootsComponent myLibraryEditorComponent;
  private final Library myLibrary;
  private final StructureLibraryTableModifiableModelProvider myModel;
  private final StructureConfigurableContext myContext;
  private final Project myProject;
  private final LibraryProjectStructureElement myProjectStructureElement;
  private boolean myUpdatingName;
  private boolean myPropertiesLoaded;

  protected LibraryConfigurable(final StructureLibraryTableModifiableModelProvider modelProvider,
                                final Library library,
                                final StructureConfigurableContext context,
                                final Runnable updateTree) {
    super(true, updateTree);
    myModel = modelProvider;
    myContext = context;
    myProject = context.getProject();
    myLibrary = library;
    myProjectStructureElement = new LibraryProjectStructureElement(context, myLibrary);
  }

  @Override
  public JComponent createOptionsPanel() {
    myLibraryEditorComponent = new LibraryRootsComponent(myProject, new Computable<LibraryEditor>() {
      @Override
      public LibraryEditor compute() {
        return getLibraryEditor();
      }
    });
    myLibraryEditorComponent.addListener(new Runnable() {
      @Override
      public void run() {
        myContext.getDaemonAnalyzer().queueUpdate(myProjectStructureElement);
        updateName();
      }
    });
    return myLibraryEditorComponent.getComponent();
  }

  @Override
  public boolean isModified() {
    return myLibraryEditorComponent != null && myLibraryEditorComponent.hasChanges();
  }

  @Override
  @NotNull
  public ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  @Override
  public void apply() {
    applyProperties();
  }

  @Override
  public void reset() {
    resetProperties();
  }

  @Override
  public void disposeUIResources() {
    if (myLibraryEditorComponent != null) {
      Disposer.dispose(myLibraryEditorComponent);
      myLibraryEditorComponent = null;
    }
  }

  @Override
  public void setDisplayName(final String name) {
    if (!myUpdatingName) {
      getLibraryEditor().setName(name);
      myContext.getDaemonAnalyzer().queueUpdateForAllElementsWithErrors();
    }
  }

  protected LibraryEditor getLibraryEditor() {
    return myModel.getModifiableModel().getLibraryEditor(myLibrary);
  }

  @Override
  public void updateName() {
    //todo[nik] pull up to NamedConfigurable
    myUpdatingName = true;
    try {
      super.updateName();
    }
    finally {
      myUpdatingName = false;
    }
  }

  @Override
  public Library getEditableObject() {
    return myLibrary;
  }

  @Override
  public String getBannerSlogan() {
    final LibraryTable libraryTable = myLibrary.getTable();
    String libraryType = libraryTable == null
                         ? ProjectBundle.message("module.library.display.name", 1)
                         : libraryTable.getPresentation().getDisplayName(false);
    return ProjectBundle.message("project.roots.library.banner.text", getDisplayName(), libraryType);
  }

  @Override
  public String getDisplayName() {
    if (myModel.getModifiableModel().hasLibraryEditor(myLibrary)) {
      return getLibraryEditor().getName();
    }

    return myLibrary.getName();
  }

  public void onSelected() {
    resetProperties();
  }

  public void onUnselected() {
    applyProperties();
  }

  private void resetProperties() {
    if (myLibraryEditorComponent != null) {
      myLibraryEditorComponent.updatePropertiesLabel();
      myLibraryEditorComponent.resetProperties();
      myPropertiesLoaded = true;
    }
  }

  private void applyProperties() {
    if (myLibraryEditorComponent != null && myPropertiesLoaded) {
      myLibraryEditorComponent.applyProperties();
      myPropertiesLoaded = false;
    }
  }

  @Override
  public Icon getIcon(boolean open) {
    return LibraryPresentationManager.getInstance().getNamedLibraryIcon(myLibrary, myContext);
  }

  @Override
  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "preferences.jdkGlobalLibs";  //todo
  }

  public void updateComponent() {
    if (myLibraryEditorComponent != null) {
      myLibraryEditorComponent.updateRootsTree();
    }
  }
}
