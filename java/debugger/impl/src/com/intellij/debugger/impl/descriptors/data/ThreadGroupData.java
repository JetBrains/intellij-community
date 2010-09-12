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

import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadGroupDescriptorImpl;
import com.intellij.openapi.project.Project;

public class ThreadGroupData extends DescriptorData<ThreadGroupDescriptorImpl>{
  private final ThreadGroupReferenceProxyImpl myThreadGroup;

  public ThreadGroupData(ThreadGroupReferenceProxyImpl threadGroup) {
    super();
    myThreadGroup = threadGroup;
  }

  protected ThreadGroupDescriptorImpl createDescriptorImpl(Project project) {
    return new ThreadGroupDescriptorImpl(myThreadGroup);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ThreadGroupData)) return false;

    return myThreadGroup.equals(((ThreadGroupData)object).myThreadGroup);
  }

  public int hashCode() {
    return myThreadGroup.hashCode();
  }

  public DisplayKey<ThreadGroupDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ThreadGroupDescriptorImpl>(myThreadGroup);
  }
}
