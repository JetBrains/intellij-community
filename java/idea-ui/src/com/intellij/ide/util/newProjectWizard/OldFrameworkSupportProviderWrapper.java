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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.framework.library.FrameworkSupportWithLibrary;
import com.intellij.ide.util.frameworkSupport.*;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class OldFrameworkSupportProviderWrapper extends FrameworkSupportInModuleProvider {
  private final FrameworkSupportProvider myProvider;
  private final OldFrameworkSupportProviderWrapper.FrameworkSupportProviderBasedType myType;

  public OldFrameworkSupportProviderWrapper(FrameworkSupportProvider provider) {
    myProvider = provider;
    myType = new FrameworkSupportProviderBasedType(myProvider, this);
  }

  public FrameworkSupportProvider getProvider() {
    return myProvider;
  }

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return myType;
  }

  @NotNull
  @Override
  public FrameworkSupportInModuleConfigurable createConfigurable(@NotNull FrameworkSupportModel model) {
    final FrameworkSupportConfigurable configurable = myProvider.createConfigurable(model);
    return new FrameworkSupportConfigurableWrapper(configurable);
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType moduleType) {
    return myProvider.isEnabledForModuleType(moduleType);
  }

  @Override
  public boolean isEnabledForModuleBuilder(@NotNull ModuleBuilder builder) {
    return myProvider.isEnabledForModuleBuilder(builder);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    return myProvider.isSupportAlreadyAdded(module, facetsProvider);
  }

  @Override
  public FrameworkRole[] getRoles() {
    return myProvider.getRoles();
  }

  public static class FrameworkSupportProviderBasedType extends FrameworkTypeEx {
    private final FrameworkSupportProvider myOldProvider;
    private final OldFrameworkSupportProviderWrapper myNewProvider;

    private FrameworkSupportProviderBasedType(FrameworkSupportProvider oldProvider,
                                              OldFrameworkSupportProviderWrapper newProvider) {
      super(oldProvider.getId());
      myOldProvider = oldProvider;
      myNewProvider = newProvider;
    }

    @NotNull
    @Override
    public FrameworkSupportInModuleProvider createProvider() {
      return myNewProvider;
    }

    @NotNull
    @Override
    public String getPresentableName() {
      return GuiUtils.getTextWithoutMnemonicEscaping(myOldProvider.getTitle());
    }

    @Override
    public String getUnderlyingFrameworkTypeId() {
      return myOldProvider.getUnderlyingFrameworkId();
    }

    @NotNull
    @Override
    public Icon getIcon() {
      final Icon icon = myOldProvider.getIcon();
      return icon != null ? icon : EmptyIcon.ICON_16;
    }

    public FrameworkSupportProvider getProvider() {
      return myOldProvider;
    }
  }

  public static class FrameworkSupportConfigurableWrapper extends FrameworkSupportInModuleConfigurable {
    private final FrameworkSupportConfigurable myConfigurable;
    private final FrameworkLibraryVersionFilter myVersionFilter;

    public FrameworkSupportConfigurableWrapper(FrameworkSupportConfigurable configurable) {
      Disposer.register(this, configurable);
      myConfigurable = configurable;
      myVersionFilter = getVersionFilter(configurable);
    }

    private FrameworkLibraryVersionFilter getVersionFilter(FrameworkSupportConfigurable configurable) {
      if (configurable instanceof FrameworkSupportWithLibrary) {
        final FrameworkLibraryVersionFilter filter = configurable.getVersionFilter();
        if (filter != null) {
          return filter;
        }
      }
      return new FrameworkLibraryVersionFilter() {
        @Override
        public boolean isAccepted(@NotNull FrameworkLibraryVersion version) {
          final FrameworkVersion selectedVersion = myConfigurable.getSelectedVersion();
          return selectedVersion == null || version.getVersionString().equals(selectedVersion.getVersionName());
        }
      };
    }

    public FrameworkSupportConfigurable getConfigurable() {
      return myConfigurable;
    }

    @Override
    public void onFrameworkSelectionChanged(boolean selected) {
      myConfigurable.onFrameworkSelectionChanged(selected);
    }

    @Override
    public boolean isVisible() {
      return myConfigurable.isVisible();
    }

    @Override
    public JComponent createComponent() {
      return myConfigurable.getComponent();
    }

    @Override
    public boolean isOnlyLibraryAdded() {
      return myConfigurable instanceof FrameworkSupportWithLibrary && ((FrameworkSupportWithLibrary)myConfigurable).isLibraryOnly();
    }

    @Override
    public CustomLibraryDescription createLibraryDescription() {
      if (myConfigurable instanceof FrameworkSupportWithLibrary) {
        return ((FrameworkSupportWithLibrary)myConfigurable).createLibraryDescription();
      }

      List<? extends FrameworkVersion> versions = myConfigurable.getVersions();
      if (versions.isEmpty()) return null;
      return OldCustomLibraryDescription.createByVersions(versions);
    }

    @NotNull
    @Override
    public FrameworkLibraryVersionFilter getLibraryVersionFilter() {
      return myVersionFilter;
    }

    @Override
    public void addSupport(@NotNull Module module,
                           @NotNull ModifiableRootModel rootModel,
                           @NotNull ModifiableModelsProvider modifiableModelsProvider) {
      myConfigurable.addSupport(module, rootModel, null);
    }
  }
}
