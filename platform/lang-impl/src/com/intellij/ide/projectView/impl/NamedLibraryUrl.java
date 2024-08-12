// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import org.jetbrains.annotations.NonNls;

public final class NamedLibraryUrl extends AbstractUrl {

  private static final @NonNls String ELEMENT_TYPE = "namedLibrary";

  public NamedLibraryUrl(String url, String moduleName) {
    super(url, moduleName, ELEMENT_TYPE);
  }

  @Override
  public Object[] createPath(Project project) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    if (module == null) return null;
    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry && orderEntry.getPresentableName().equals(url)) {
        return new Object[]{new NamedLibraryElement(module, (LibraryOrSdkOrderEntry)orderEntry)};
      }
    }
    return null;
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new NamedLibraryUrl(url, moduleName);
  }

  @Override
  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof NamedLibraryElement libraryElement) {

      Module context = libraryElement.getModule();
      
      return new NamedLibraryUrl(libraryElement.getOrderEntry().getPresentableName(), context != null ? context.getName() : null);
    }
    return null;
  }
}
