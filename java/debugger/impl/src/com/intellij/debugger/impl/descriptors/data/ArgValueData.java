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

import com.intellij.debugger.ui.impl.watch.ArgumentValueDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

public class ArgValueData extends DescriptorData<ArgumentValueDescriptorImpl>{
  private final int myIndex;
  private final Value myValue;
  @Nullable
  private final String myDisplayName;

  public ArgValueData(int index, Value value, @Nullable String displayName) {
    super();
    myIndex = index;
    myValue = value;
    myDisplayName = displayName;
  }

  protected ArgumentValueDescriptorImpl createDescriptorImpl(Project project) {
    return new ArgumentValueDescriptorImpl(project, myIndex, myValue, myDisplayName);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ArgValueData)) return false;

    return myIndex == ((ArgValueData)object).myIndex;
  }

  public int hashCode() {
    return myIndex;
  }

  public DisplayKey<ArgumentValueDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ArgumentValueDescriptorImpl>(myIndex);
  }
}