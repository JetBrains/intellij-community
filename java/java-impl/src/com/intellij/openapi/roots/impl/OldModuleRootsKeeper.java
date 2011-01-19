/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 *  @author dsl
 */
public class OldModuleRootsKeeper implements ModuleComponent, JDOMExternalizable {
  private Element myElement;

  public static OldModuleRootsKeeper getInstance(Module module) {
    return module.getComponent(OldModuleRootsKeeper.class);
  }

  public OldModuleRootsKeeper(Module module) {

  }

  @NotNull
  public String getComponentName() {
    return "ModuleRootManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    myElement = (Element)element.clone();
  }


  public void writeExternal(Element element) throws WriteExternalException {
    if (myElement != null) {
      final List children = myElement.getChildren();
      for (final Object aChildren : children) {
        Element child = (Element)aChildren;
        element.addContent((Element)child.clone());
      }
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
  }

}
