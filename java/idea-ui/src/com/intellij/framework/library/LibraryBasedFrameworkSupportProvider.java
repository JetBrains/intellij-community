// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LibraryBasedFrameworkSupportProvider extends FrameworkSupportInModuleProvider {
  private final FrameworkTypeEx myFrameworkType;
  private final Class<? extends DownloadableLibraryType> myLibraryTypeClass;

  public LibraryBasedFrameworkSupportProvider(FrameworkTypeEx frameworkType, Class<? extends DownloadableLibraryType> libraryTypeClass) {
    myFrameworkType = frameworkType;
    myLibraryTypeClass = libraryTypeClass;
  }

  @Override
  public @NotNull FrameworkTypeEx getFrameworkType() {
    return myFrameworkType;
  }

  @Override
  public @NotNull FrameworkSupportInModuleConfigurable createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new LibrarySupportConfigurable();
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType<?> moduleType) {
    return moduleType instanceof JavaModuleType;
  }

  private class LibrarySupportConfigurable extends FrameworkSupportInModuleConfigurable {
    @Override
    public JComponent createComponent() {
      return null;
    }

    @Override
    public void addSupport(@NotNull Module module,
                           @NotNull ModifiableRootModel rootModel,
                           @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    }

    @Override
    public @NotNull CustomLibraryDescription createLibraryDescription() {
      return DownloadableLibraryService.getInstance().createDescriptionForType(myLibraryTypeClass);
    }

    @Override
    public boolean isOnlyLibraryAdded() {
      return true;
    }
  }
}
