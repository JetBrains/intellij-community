/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.AreaInstance;

/**
 * @author akireyev
 */
class AreaDescriptor {
  private String myAreaClass;
  private AreaInstance myInstance;

  public AreaDescriptor(String aClass, AreaInstance instance) {
    myAreaClass = aClass;
    myInstance = instance;
  }

  public String getAreaClass() {
    return myAreaClass;
  }

  public AreaInstance getAreaInstance() {
    return myInstance;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AreaDescriptor)) return false;

    final AreaDescriptor areaDescriptor = (AreaDescriptor) o;

    if (myAreaClass != null ? !myAreaClass.equals(areaDescriptor.myAreaClass) : areaDescriptor.myAreaClass != null) return false;
    if (myInstance != null ? !myInstance.equals(areaDescriptor.myInstance) : areaDescriptor.myInstance != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myAreaClass != null ? myAreaClass.hashCode() : 0);
    result = 29 * result + (myInstance != null ? myInstance.hashCode() : 0);
    return result;
  }
}
