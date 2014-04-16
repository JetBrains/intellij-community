/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class JavaExecutionStack extends XExecutionStack {
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final DebugProcessImpl myDebugProcess;
  private final XStackFrame myTopFrame;

  public JavaExecutionStack(ThreadReferenceProxyImpl threadProxy, DebugProcessImpl debugProcess) {
    super("stack");
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
    XStackFrame topFrame = null;
    try {
      topFrame = new JavaStackFrame(myThreadProxy.frame(0), myDebugProcess);
    }
    catch (EvaluateException e) {
      e.printStackTrace();
    }
    myTopFrame = topFrame;
  }

  @Nullable
  @Override
  public XStackFrame getTopFrame() {
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
    try {
      List<JavaStackFrame> frames = new ArrayList<JavaStackFrame>();
      boolean top = true;
      for (StackFrameProxyImpl frameProxy : myThreadProxy.frames()) {
        if (top) {
          top = false;
          continue;
        }
        frames.add(new JavaStackFrame(frameProxy, myDebugProcess));
      }
      container.addStackFrames(frames, true);
    }
    catch (EvaluateException e) {
      e.printStackTrace();
    }
  }
}
