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

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.jdi;

import com.sun.jdi.StringReference;

public class StringReferenceProxy extends ObjectReferenceProxyImpl{
  private String myStringValue;

  public StringReferenceProxy(VirtualMachineProxyImpl virtualMachineProxy, StringReference objectReference) {
    super(virtualMachineProxy, objectReference);
  }

  public StringReference getStringReference() {
    return (StringReference)getObjectReference();
  }

  public String value() {
    checkValid();
    if (myStringValue == null) {
      myStringValue = getStringReference().value();
    }
    return myStringValue;
  }

  public void clearCaches() {
    myStringValue = null;
    super.clearCaches();
  }
}
