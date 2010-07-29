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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.xdebugger.impl.frame.DebuggerFramesList;
import com.sun.jdi.Method;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 7, 2006
 */
public class FramesList extends DebuggerFramesList {
  private volatile Method mySelectedMethod = null;

  protected FramesListRenderer createListRenderer() {
    return new FramesListRenderer();
  }

  protected void onFrameChanged(final Object selectedValue) {
    final StackFrameDescriptorImpl descriptor = selectedValue instanceof StackFrameDescriptorImpl? (StackFrameDescriptorImpl)selectedValue : null;
    final Method newMethod = descriptor != null? descriptor.getMethod() : null;
    if (!Comparing.equal(mySelectedMethod, newMethod)) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          repaint();
        }
      });
    }
    mySelectedMethod = newMethod;
  }

}
