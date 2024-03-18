/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

/**
 * @author Nikolay.Tropin
 */
public final class DefaultSyntheticProvider implements SyntheticTypeComponentProvider {

  @Override
  public boolean isSynthetic(@NotNull TypeComponent typeComponent) {
    if (DebuggerUtilsEx.isLambdaClassName(typeComponent.declaringType().name())) {
      return true;
    }

    VirtualMachine vm = typeComponent.virtualMachine();
    return vm != null && vm.canGetSyntheticAttribute() ? typeComponent.isSynthetic() : typeComponent.name().contains("$");
  }

  @Override
  public boolean isNotSynthetic(TypeComponent typeComponent) {
    String name = typeComponent.name();
    return DebuggerUtilsEx.isLambdaName(name);
  }
}
