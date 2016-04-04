/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.sun.jdi.TypeComponent;
import com.sun.jdi.VirtualMachine;

/**
 * @author Nikolay.Tropin
 */
public class DefaultSyntheticProvider implements SyntheticTypeComponentProvider {
  @Override
  public boolean isSynthetic(TypeComponent typeComponent) {
    String name = typeComponent.name();
    if (LambdaMethodFilter.isLambdaName(name)) {
      return false;
    }
    else {
      if (DebuggerUtilsEx.isLambdaClassName(typeComponent.declaringType().name())) {
        return true;
      }
    }
    VirtualMachine machine = typeComponent.virtualMachine();
    if (machine != null && machine.canGetSyntheticAttribute()) {
      return typeComponent.isSynthetic();
    }
    else {
      return name.contains("$");
    }
  }
}
