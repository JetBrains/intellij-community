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

import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.project.Project;

public class ThreadData extends DescriptorData<ThreadDescriptorImpl> {
  private final ThreadReferenceProxyImpl myThread;
  public ThreadData(ThreadReferenceProxyImpl thread) {
    super();
    myThread = thread;
  }

  protected ThreadDescriptorImpl createDescriptorImpl(Project project) {
    return new ThreadDescriptorImpl(myThread);
  }

  public boolean equals(Object object) {
    if(!(object instanceof ThreadData)) {
      return false;
    }
    return myThread.equals(((ThreadData)object).myThread);
  }

  public int hashCode() {
    return myThread.hashCode();
  }

  public DisplayKey<ThreadDescriptorImpl> getDisplayKey() {
    return new SimpleDisplayKey<ThreadDescriptorImpl>(myThread);
  }
}
