/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

public class ExtensionClassAndAreaInstance {
  private final Class myExtensionClass;
  private final Object myAreaInstance;

  public ExtensionClassAndAreaInstance(Class extensionClass, Object areaInstance) {
    myExtensionClass = extensionClass;
    myAreaInstance = areaInstance;
  }

  public Class getExtensionClass() {
    return myExtensionClass;
  }

  public Object getAreaInstance() {
    return myAreaInstance;
  }

  public boolean equals(Object areaInstance) {
    if (this == areaInstance) return true;
    if (!(areaInstance instanceof ExtensionClassAndAreaInstance)) return false;

    final ExtensionClassAndAreaInstance extensionClassAndAreaInstance = (ExtensionClassAndAreaInstance) areaInstance;

    if (myAreaInstance != null ? !myAreaInstance.equals(extensionClassAndAreaInstance.myAreaInstance) : extensionClassAndAreaInstance.myAreaInstance != null) return false;
    if (!myExtensionClass.equals(extensionClassAndAreaInstance.myExtensionClass)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myExtensionClass.hashCode();
    result = 29 * result + (myAreaInstance != null ? myAreaInstance.hashCode() : 0);
    return result;
  }
}
