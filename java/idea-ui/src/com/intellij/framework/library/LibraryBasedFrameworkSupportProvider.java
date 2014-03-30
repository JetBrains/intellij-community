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

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return myFrameworkType;
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleConfigurable createConfigurable(@NotNull final FrameworkSupportModel model) {
    return new LibrarySupportConfigurable();
  }

  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
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

    @NotNull
    @Override
    public CustomLibraryDescription createLibraryDescription() {
      return DownloadableLibraryService.getInstance().createDescriptionForType(myLibraryTypeClass);
    }

    @Override
    public boolean isOnlyLibraryAdded() {
      return true;
    }
  }
}
