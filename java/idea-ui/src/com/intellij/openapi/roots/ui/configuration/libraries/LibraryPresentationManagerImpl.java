/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraries;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class LibraryPresentationManagerImpl extends LibraryPresentationManager {
  private Map<LibraryKind<?>, LibraryPresentationProvider<?>> myPresentationProviders;

  private <P extends LibraryProperties> LibraryPresentationProvider<P> getPresentationProvider(LibraryKind<P> kind) {
    if (myPresentationProviders == null) {
      final Map<LibraryKind<?>, LibraryPresentationProvider<?>> providers = new HashMap<LibraryKind<?>, LibraryPresentationProvider<?>>();
      for (LibraryPresentationProvider provider : LibraryPresentationProvider.EP_NAME.getExtensions()) {
        providers.put(provider.getKind(), provider);
      }
      myPresentationProviders = providers;
    }
    //noinspection unchecked
    return (LibraryPresentationProvider<P>)myPresentationProviders.get(kind);
  }

  @Override
  public Icon getCustomIcon(@NotNull Library library, StructureConfigurableContext context) {
    final List<Icon> icons = getCustomIcons(library, context);
    if (icons.size() == 1) {
      return icons.get(0);
    }
    return null;
  }

  @NotNull
  @Override
  public List<Icon> getCustomIcons(@NotNull Library library, StructureConfigurableContext context) {
    final VirtualFile[] files = getLibraryFiles(library, context);
    final List<Icon> icons = new SmartList<Icon>();
    LibraryDetectionManager.getInstance().processProperties(Arrays.asList(files), new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> kind, @NotNull P properties) {
        final LibraryPresentationProvider<P> provider = getPresentationProvider(kind);
        if (provider != null) {
          ContainerUtil.addIfNotNull(icons, provider.getIcon());
        }
        return true;
      }
    });
    return icons;
  }

  private static VirtualFile[] getLibraryFiles(Library library, StructureConfigurableContext context) {
    if (context != null) {
      final LibraryTable table = library.getTable();
      if (table != null) {
        final LibraryTable.ModifiableModel modifiableModel = context.getModifiableLibraryTable(table);
        if (modifiableModel instanceof LibrariesModifiableModel) {
          final LibrariesModifiableModel librariesModel = (LibrariesModifiableModel)modifiableModel;
          if (librariesModel.hasLibraryEditor(library)) {
            return librariesModel.getLibraryEditor(library).getFiles(OrderRootType.CLASSES);
          }
        }
      }
    }
    return library.getFiles(OrderRootType.CLASSES);
  }

  @NotNull
  @Override
  public List<String> getDescriptions(@NotNull Library library, StructureConfigurableContext context) {
    final VirtualFile[] files = getLibraryFiles(library, context);
    return getDescriptions(files);
  }

  @NotNull
  @Override
  public List<String> getDescriptions(@NotNull VirtualFile[] classRoots) {
    final SmartList<String> result = new SmartList<String>();
    LibraryDetectionManager.getInstance().processProperties(Arrays.asList(classRoots), new LibraryDetectionManager.LibraryPropertiesProcessor() {
      @Override
      public <P extends LibraryProperties> boolean processProperties(@NotNull LibraryKind<P> kind, @NotNull P properties) {
        final LibraryPresentationProvider<P> provider = getPresentationProvider(kind);
        if (provider != null) {
          ContainerUtil.addIfNotNull(result, provider.getDescription(properties));
        }
        return true;
      }
    });
    return result;
  }
}
