/**
 * @author cdr
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.javaee.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.Nullable;

public interface ModuleContainer extends TransactionalEditable, JDOMExternalizable {
  ModuleLink[] getContainingModules();

  @Nullable
  ModuleLink findModuleLink(Module module);

  LibraryLink[] getContainingLibraries();

  ContainerElement[] getElements();

  void setElements(ContainerElement[] elements);

  void addElement(ContainerElement element);

  void removeModule(Module module);

  void containedEntriesChanged();

  void disposeModifiableModel();
}