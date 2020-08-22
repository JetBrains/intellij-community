// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.facet.impl.ui.libraries.LibraryOptionsPanel;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ParameterizedRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class AddCustomLibraryDialog extends DialogWrapper {
  private final LibraryOptionsPanel myPanel;
  private final LibrariesContainer myLibrariesContainer;
  private final Module myModule;
  private final ModifiableRootModel myModifiableRootModel;
  private final @Nullable ParameterizedRunnable<? super ModifiableRootModel> myBeforeLibraryAdded;
  private final List<Library> myAddedLibraries = new ArrayList<>();

  private AddCustomLibraryDialog(CustomLibraryDescription description, LibrariesContainer librariesContainer,
                                 Module module,
                                 ModifiableRootModel modifiableRootModel,
                                 @Nullable ParameterizedRunnable<? super ModifiableRootModel> beforeLibraryAdded) {
    super(module.getProject(), true);
    myLibrariesContainer = librariesContainer;
    myModule = module;
    myModifiableRootModel = modifiableRootModel;
    myBeforeLibraryAdded = beforeLibraryAdded;
    setTitle(JavaUiBundle.message("setup.library.dialog.title"));
    VirtualFile baseDir = myModule.getProject().getBaseDir();
    final String baseDirPath = baseDir != null ? baseDir.getPath() : "";
    myPanel = new LibraryOptionsPanel(description, baseDirPath, FrameworkLibraryVersionFilter.ALL, myLibrariesContainer, false);
    Disposer.register(myDisposable, myPanel);
    init();
  }

  public static AddCustomLibraryDialog createDialog(@NotNull CustomLibraryDescription description,
                                                    final @NotNull Module module,
                                                    final ParameterizedRunnable<? super ModifiableRootModel> beforeLibraryAdded) {
    return createDialog(description, LibrariesContainerFactory.createContainer(module), module, null, beforeLibraryAdded);
  }

  public static AddCustomLibraryDialog createDialog(CustomLibraryDescription description,
                                                    final @NotNull LibrariesContainer librariesContainer, final @NotNull Module module,
                                                    final @Nullable ModifiableRootModel modifiableRootModel,
                                                    @Nullable ParameterizedRunnable<? super ModifiableRootModel> beforeLibraryAdded) {
    return new AddCustomLibraryDialog(description, librariesContainer, module, modifiableRootModel, beforeLibraryAdded);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getMainPanel();
  }

  @Override
  protected void doOKAction() {
    final LibraryCompositionSettings settings = myPanel.apply();
    if (settings != null && settings.downloadFiles(myPanel.getMainPanel())) {
      if (myModifiableRootModel == null) {
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        WriteAction.run(() -> {
          addLibraries(model, settings);
          model.commit();
        });
      }
      else {
        addLibraries(myModifiableRootModel, settings);
      }

    }
    super.doOKAction();
  }

  private void addLibraries(ModifiableRootModel model, final LibraryCompositionSettings settings) {
    if (myBeforeLibraryAdded != null) {
      myBeforeLibraryAdded.run(model);
    }
    settings.addLibraries(model, myAddedLibraries, myLibrariesContainer);
  }

  public List<Library> getAddedLibraries() {
    return myAddedLibraries;
  }
}
