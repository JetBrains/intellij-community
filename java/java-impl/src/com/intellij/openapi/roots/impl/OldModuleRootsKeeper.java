package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

import java.util.Iterator;
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
