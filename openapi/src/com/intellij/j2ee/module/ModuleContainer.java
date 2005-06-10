/**
 * @author cdr
 */
package com.intellij.j2ee.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.JDOMExternalizable;

public interface ModuleContainer extends TransactionalEditable, JDOMExternalizable {
  ModuleLink[] getContainingModules();

  LibraryLink[] getContainingLibraries();

  ContainerElement[] getElements();

  void setElements(ContainerElement[] elements);

  void addElement(ContainerElement element);

  void removeModule(Module module);

  void containedEntriesChanged();

  void disposeModifiableModel();
}