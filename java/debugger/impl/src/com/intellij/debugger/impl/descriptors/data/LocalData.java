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

import com.intellij.debugger.jdi.LocalVariableProxyImpl;
import com.intellij.debugger.ui.impl.watch.LocalVariableDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;

public class LocalData extends DescriptorData<LocalVariableDescriptorImpl>{
  private final LocalVariableProxyImpl myLocalVariable;

  public LocalData(LocalVariableProxyImpl localVariable) {
    super();
    myLocalVariable = localVariable;
  }

  protected LocalVariableDescriptorImpl createDescriptorImpl(@NotNull Project project) {
    return new LocalVariableDescriptorImpl(project, myLocalVariable);
  }

  public boolean equals(Object object) {
    if(!(object instanceof LocalData)) return false;

    return ((LocalData)object).myLocalVariable.equals(myLocalVariable);
  }

  public int hashCode() {
    return myLocalVariable.hashCode();
  }

  public DisplayKey<LocalVariableDescriptorImpl> getDisplayKey() {
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      return new SimpleDisplayKey<>(builder.append(myLocalVariable.typeName()).append("#").append(myLocalVariable.name()).toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }
}
