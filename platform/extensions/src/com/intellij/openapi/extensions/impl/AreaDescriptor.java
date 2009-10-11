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
package com.intellij.openapi.extensions.impl;

import com.intellij.openapi.extensions.AreaInstance;

/**
 * @author akireyev
 */
class AreaDescriptor {
  private final String myAreaClass;
  private final AreaInstance myInstance;

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
