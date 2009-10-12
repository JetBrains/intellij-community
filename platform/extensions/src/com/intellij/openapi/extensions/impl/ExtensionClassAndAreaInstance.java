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

class ExtensionClassAndAreaInstance {
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
