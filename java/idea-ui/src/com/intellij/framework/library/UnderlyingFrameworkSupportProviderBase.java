package com.intellij.framework.library;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurableBase;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderBase;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class UnderlyingFrameworkSupportProviderBase extends FrameworkSupportProviderBase {

  public UnderlyingFrameworkSupportProviderBase(final @NonNls @NotNull String id, final @NotNull String title) {
    super(id, title);
  }

  @Override
  protected void addSupport(@NotNull Module module,
                            @NotNull ModifiableRootModel rootModel,
                            FrameworkVersion version,
                            @Nullable Library library) {
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurableBase createConfigurable(@NotNull final FrameworkSupportModel model) {
    return new LibrarySupportConfigurable(this, model);
  }

  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return true;
  }

  protected abstract Class<? extends DownloadableLibraryType> getLibraryClass() ;

  private class LibrarySupportConfigurable extends FrameworkSupportConfigurableBase implements FrameworkSupportWithLibrary {
    private LibrarySupportConfigurable(UnderlyingFrameworkSupportProviderBase provider,
                                       FrameworkSupportModel model) {
      super(provider, model);
    }

    @NotNull
    @Override
    public CustomLibraryDescription createLibraryDescription() {
      return DownloadableLibraryService.getInstance().createDescriptionForType(getLibraryClass());
    }

    @Override
    public boolean isLibraryOnly() {
      return false;
    }
  }
}
