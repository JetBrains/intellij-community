/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.framework.addSupport.impl;

import com.intellij.CommonBundle;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.facet.impl.ui.libraries.LibraryCompositionSettings;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModelImpl;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportOptionsComponent;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class AddSupportForSingleFrameworkDialog extends DialogWrapper {
  private final Module myModule;
  private final FrameworkSupportInModuleConfigurable myConfigurable;
  private final FrameworkSupportModelBase myModel;
  private final FrameworkSupportOptionsComponent myComponent;
  private final FrameworkTypeEx myFrameworkType;
  private final ModifiableModelsProvider myModifiableModelsProvider;

  public AddSupportForSingleFrameworkDialog(@NotNull Module module,
                                            FrameworkTypeEx frameworkType, @NotNull FrameworkSupportInModuleProvider provider,
                                            @NotNull LibrariesContainer librariesContainer,
                                            ModifiableModelsProvider modifiableModelsProvider) {
    super(module.getProject(), true);
    VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
    final VirtualFile baseDir = roots.length > 0 ? roots[0] : module.getProject().getBaseDir();
    final String baseDirectoryForLibraries = baseDir != null ? baseDir.getPath() : "";
    myFrameworkType = frameworkType;
    myModifiableModelsProvider = modifiableModelsProvider;
    setTitle(ProjectBundle.message("dialog.title.add.framework.0.support", frameworkType.getPresentableName()));
    myModule = module;
    myModel = new FrameworkSupportModelImpl(module.getProject(), baseDirectoryForLibraries, librariesContainer);
    myConfigurable = provider.createConfigurable(myModel);
    myComponent = new FrameworkSupportOptionsComponent(myModel, myModel.getLibrariesContainer(), myDisposable, provider, myConfigurable);
    Disposer.register(myDisposable, myConfigurable);
    init();
  }

  public static AddSupportForSingleFrameworkDialog createDialog(@NotNull Module module,
                                                                @NotNull FrameworkSupportInModuleProvider provider) {
    List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getProviders(module, DefaultFacetsProvider.INSTANCE);
    if (providers.isEmpty()) return null;

    IdeaModifiableModelsProvider modifiableModelsProvider = new IdeaModifiableModelsProvider();
    LibrariesContainer container = LibrariesContainerFactory.createContainer(modifiableModelsProvider.getModuleModifiableModel(module));

    return new AddSupportForSingleFrameworkDialog(module, provider.getFrameworkType(), provider, container, modifiableModelsProvider);
  }

  protected void doOKAction() {
    final Ref<Boolean> result = Ref.create(false);
    DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, () -> result.set(addSupport()));

    if (result.get()) {
      super.doOKAction();
    }
  }

  private boolean addSupport() {
    final LibraryCompositionSettings librarySettings = myComponent.getLibraryCompositionSettings();
    if (librarySettings != null) {
      final ModifiableRootModel modifiableModel = myModifiableModelsProvider.getModuleModifiableModel(myModule);
      if (!askAndRemoveDuplicatedLibraryEntry(modifiableModel, librarySettings.getLibraryDescription())) {
        if (myConfigurable.isOnlyLibraryAdded()) {
          myModifiableModelsProvider.disposeModuleModifiableModel(modifiableModel);
          return false;
        }
        return false;
      }

      new WriteAction() {
        protected void run(@NotNull final Result result) {
          myModifiableModelsProvider.commitModuleModifiableModel(modifiableModel);
        }
      }.execute();

      final boolean downloaded = librarySettings.downloadFiles(getRootPane());
      if (!downloaded) {
        int answer = Messages.showYesNoDialog(getRootPane(),
                                              ProjectBundle.message("warning.message.some.required.libraries.wasn.t.downloaded"),
                                              CommonBundle.getWarningTitle(), Messages.getWarningIcon());
        if (answer != Messages.YES) {
          return false;
        }
      }
    }

    new WriteAction() {
      protected void run(@NotNull final Result result) {
        final ModifiableRootModel rootModel = myModifiableModelsProvider.getModuleModifiableModel(myModule);
        if (librarySettings != null) {
          librarySettings.addLibraries(rootModel, new ArrayList<>(), myModel.getLibrariesContainer());
        }
        myConfigurable.addSupport(myModule, rootModel, myModifiableModelsProvider);
        myModifiableModelsProvider.commitModuleModifiableModel(rootModel);
      }
    }.execute();
    return true;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.framework.addSupport.AddSupportForSingleFrameworkDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.frameworks.support.dialog";//todo[nik]
  }

  protected JComponent createCenterPanel() {
    return myComponent.getMainPanel();
  }

  private boolean askAndRemoveDuplicatedLibraryEntry(@NotNull ModifiableRootModel rootModel, @NotNull CustomLibraryDescription description) {
    List<OrderEntry> existingEntries = new ArrayList<>();
    final LibrariesContainer container = myModel.getLibrariesContainer();
    for (OrderEntry entry : rootModel.getOrderEntries()) {
      if (!(entry instanceof LibraryOrderEntry)) continue;
      final Library library = ((LibraryOrderEntry)entry).getLibrary();
      if (library == null) continue;

      if (LibraryPresentationManager.getInstance().isLibraryOfKind(library, container, description.getSuitableLibraryKinds())) {
        existingEntries.add(entry);
      }
    }

    if (!existingEntries.isEmpty()) {
      String message;
      if (existingEntries.size() > 1) {
        message = "There are already " + existingEntries.size() + " " + myFrameworkType.getPresentableName() + " libraries.\n Do you want to replace them?";
      }
      else {
        final String name = existingEntries.get(0).getPresentableName();
        message = "There is already a " + myFrameworkType.getPresentableName() + " library '" + name + "'.\n Do you want to replace it?";
      }
      final int result = Messages.showYesNoCancelDialog(rootModel.getProject(), message, "Library Already Exists",
                                                        "&Replace", "&Add", "&Cancel", null);
      if (result == Messages.YES) {
        for (OrderEntry entry : existingEntries) {
          rootModel.removeOrderEntry(entry);
        }
      }
      else if (result != Messages.NO) {
        return false;
      }
    }
    return true;
  }
}
