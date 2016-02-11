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
package com.intellij.debugger.impl.descriptors.data;

import com.intellij.debugger.ui.impl.watch.ThisDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public final class ThisData extends DescriptorData<ThisDescriptorImpl>{

  private static final Key THIS = new Key("THIS");

  protected ThisDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new ThisDescriptorImpl(project);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ThisData)) return false;

    return true;
  }

  public int hashCode() {
    return THIS.hashCode();
  }

  public DisplayKey<ThisDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<>(THIS);
  }
}
