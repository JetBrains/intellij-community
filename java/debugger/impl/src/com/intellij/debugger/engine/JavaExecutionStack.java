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
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.xdebugger.frame.XExecutionStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class JavaExecutionStack extends XExecutionStack {
  private final ThreadReferenceProxyImpl myThreadProxy;
  private final DebugProcessImpl myDebugProcess;
  private final JavaStackFrame myTopFrame;
  private final MethodsTracker myTracker = new MethodsTracker();

  public JavaExecutionStack(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull DebugProcessImpl debugProcess) {
    super(threadProxy.name());
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
    JavaStackFrame topFrame = null;
    try {
      StackFrameProxyImpl frame = myThreadProxy.frame(0);
      if (frame != null) {
        topFrame = new JavaStackFrame(frame, myDebugProcess, myTracker);
      }
    }
    catch (EvaluateException e) {
      e.printStackTrace();
    }
    myTopFrame = topFrame;
  }

  @Nullable
  @Override
  public JavaStackFrame getTopFrame() {
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(int firstFrameIndex, final XStackFrameContainer container) {
    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        List<JavaStackFrame> frames = new ArrayList<JavaStackFrame>();
        boolean top = true;
        for (StackFrameProxyImpl frameProxy : myThreadProxy.frames()) {
          if (top) {
            top = false;
            continue;
          }
          frames.add(new JavaStackFrame(frameProxy, myDebugProcess, myTracker));
        }
        container.addStackFrames(frames, true);
      }
    });
  }
}
