package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.impl.nodes.LibraryGroupElement;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class NamedLibraryUrl extends AbstractUrl {

  @NonNls private static final String ELEMENT_TYPE = "namedLibrary";

  public NamedLibraryUrl(String url, String moduleName) {
    super(url, moduleName, ELEMENT_TYPE);
  }

  public Object[] createPath(Project project) {
    final Module module = moduleName != null ? ModuleManager.getInstance(project).findModuleByName(moduleName) : null;
    if (module == null) return null;
    final OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      if (orderEntry instanceof LibraryOrderEntry || orderEntry instanceof JdkOrderEntry && orderEntry.getPresentableName().equals(url)){
        return new Object[]{new NamedLibraryElement(new LibraryGroupElement(module), orderEntry)};
      }
    }
    return null;
  }

  protected AbstractUrl createUrl(String moduleName, String url) {
      return new NamedLibraryUrl(url, moduleName);
  }

  public AbstractUrl createUrlByElement(Object element) {
    if (element instanceof NamedLibraryElement) {
      NamedLibraryElement libraryElement = (NamedLibraryElement)element;

      return new NamedLibraryUrl(libraryElement.getOrderEntry().getPresentableName(), libraryElement.getParent().getModule() != null ? libraryElement.getParent().getModule().getName() : null);
    }
    return null;
  }
}
