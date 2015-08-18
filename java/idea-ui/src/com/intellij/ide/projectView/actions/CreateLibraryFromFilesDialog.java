/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.projectView.actions;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryTypeServiceImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryNameAndLevelPanel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class CreateLibraryFromFilesDialog extends DialogWrapper {
  private final LibraryNameAndLevelPanel myNameAndLevelPanel;
  private final ModulesComboBox myModulesComboBox;
  private final Project myProject;
  private final List<OrderRoot> myRoots;
  private final JPanel myPanel;
  private final String myDefaultName;

  public CreateLibraryFromFilesDialog(@NotNull Project project, @NotNull List<OrderRoot> roots) {
    super(project, true);
    setTitle("Create Library");
    myProject = project;
    myRoots = roots;
    final FormBuilder builder = LibraryNameAndLevelPanel.createFormBuilder();
    myDefaultName =
      LibrariesContainerFactory.createContainer(project).suggestUniqueLibraryName(LibraryTypeServiceImpl.suggestLibraryName(roots));
    myNameAndLevelPanel = new LibraryNameAndLevelPanel(builder, myDefaultName, Arrays.asList(LibrariesContainer.LibraryLevel.values()),
                                                       LibrariesContainer.LibraryLevel.PROJECT);
    myNameAndLevelPanel.setDefaultName(myDefaultName);
    myModulesComboBox = new ModulesComboBox();
    myModulesComboBox.fillModules(myProject);
    myModulesComboBox.setSelectedModule(findModule(roots));
    builder.addLabeledComponent("&Add to module:", myModulesComboBox);
    myPanel = builder.getPanel();
    myNameAndLevelPanel.getLibraryNameField().selectAll();
    myNameAndLevelPanel.getLevelComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onLevelChanged();
      }
    });
    myNameAndLevelPanel.getLibraryNameField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateOkAction();
      }
    });
    init();
  }

  private void updateOkAction() {
    setOKActionEnabled(!myNameAndLevelPanel.getLibraryName().isEmpty()
                       || myNameAndLevelPanel.getLibraryLevel() == LibrariesContainer.LibraryLevel.MODULE && myRoots.size() == 1);
  }

  private void onLevelChanged() {
    if (myNameAndLevelPanel.getLibraryLevel() == LibrariesContainer.LibraryLevel.MODULE) {
      myNameAndLevelPanel.setDefaultName(myRoots.size() == 1 ? "" : myDefaultName);
    }
    else {
      myNameAndLevelPanel.setDefaultName(myDefaultName);
      if (myNameAndLevelPanel.getLibraryName().isEmpty()) {
        myNameAndLevelPanel.getLibraryNameField().setText(myDefaultName);
      }
    }
    updateOkAction();
  }

  @Nullable
  private Module findModule(List<OrderRoot> roots) {
    for (OrderRoot root : roots) {
      Module module = null;
      final VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(root.getFile());
      if (local != null) {
        module = ModuleUtil.findModuleForFile(local, myProject);
      }
      if (module == null) {
        module = ModuleUtil.findModuleForFile(root.getFile(), myProject);
      }
      if (module != null) {
        return module;
      }
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameAndLevelPanel.getLibraryNameField();
  }

  @Override
  protected void doOKAction() {
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
      @Override
      public void run() {
        addLibrary();
      }
    });
    super.doOKAction();
  }

  private void addLibrary() {
    final LibrariesContainer.LibraryLevel level = myNameAndLevelPanel.getLibraryLevel();
    AccessToken token = WriteAction.start();
    try {
      final Module module = myModulesComboBox.getSelectedModule();
      final String libraryName = myNameAndLevelPanel.getLibraryName();
      if (level == LibrariesContainer.LibraryLevel.MODULE) {
        final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
        LibrariesContainerFactory.createContainer(modifiableModel).createLibrary(libraryName, level, myRoots);
        modifiableModel.commit();
      }
      else {
        final Library library = LibrariesContainerFactory.createContainer(myProject).createLibrary(libraryName, level, myRoots);
        if (module != null) {
          ModuleRootModificationUtil.addDependency(module, library);
        }
      }
    }
    finally {
      token.finish();
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }
}
