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

import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public abstract class DescriptorData <T extends NodeDescriptor> implements DescriptorKey<T>{
  private static final Key DESCRIPTOR_DATA = new Key("DESCRIPTOR_DATA");

  protected DescriptorData() {
  }

  public T createDescriptor(@NotNull Project project) {
    T descriptor = createDescriptorImpl(project);
    descriptor.putUserData(DESCRIPTOR_DATA, this);
    return descriptor;
  }

  protected abstract T createDescriptorImpl(@NotNull Project project);

  public abstract boolean equals(Object object);

  public abstract int hashCode();

  public abstract DisplayKey<T> getDisplayKey();

  public static <T extends NodeDescriptor> DescriptorData<T> getDescriptorData(T descriptor) {
    return (DescriptorData<T>)descriptor.getUserData(DESCRIPTOR_DATA);
  }
}
