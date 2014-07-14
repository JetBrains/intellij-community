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
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.sun.jdi.Method;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MethodReturnValueData extends DescriptorData<MethodReturnValueDescriptorImpl>{
  private final @Nullable Value myReturnValue;
  private final @NotNull Method myMethod;

  public MethodReturnValueData(@NotNull Method method, @Nullable Value returnValue) {
    super();
    myMethod = method;
    myReturnValue = returnValue;
  }

  public @Nullable Value getReturnValue() {
    return myReturnValue;
  }

  public @NotNull Method getMethod() {
    return myMethod;
  }

  protected MethodReturnValueDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new MethodReturnValueDescriptorImpl(project, myMethod, myReturnValue);
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MethodReturnValueData that = (MethodReturnValueData)o;

    if (!myMethod.equals(that.myMethod)) return false;
    if (myReturnValue != null ? !myReturnValue.equals(that.myReturnValue) : that.myReturnValue != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myReturnValue != null ? myReturnValue.hashCode() : 0);
    result = 31 * result + myMethod.hashCode();
    return result;
  }

  public DisplayKey<MethodReturnValueDescriptorImpl> getDisplayKey() {
    return new MethodReturnValueDisplayKey(myMethod, myReturnValue);
  }

  private static final class MethodReturnValueDisplayKey extends Pair<Method, Value> implements DisplayKey<MethodReturnValueDescriptorImpl> {
    public MethodReturnValueDisplayKey(@NotNull Method method, @Nullable Value value) {
      super(method, value);
    }
  }
}
