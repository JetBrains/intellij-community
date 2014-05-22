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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.impl.watch.MethodsTracker;
import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.sun.jdi.ThreadReference;
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
  private volatile JavaStackFrame myTopFrame;
  private boolean myTopFrameReady = false;
  private final MethodsTracker myTracker = new MethodsTracker();

  public JavaExecutionStack(@NotNull ThreadReferenceProxyImpl threadProxy, @NotNull DebugProcessImpl debugProcess, boolean current) {
    super(calcRepresentation(threadProxy), current ? AllIcons.Debugger.ThreadCurrent : AllIcons.Debugger.ThreadSuspended);
    myThreadProxy = threadProxy;
    myDebugProcess = debugProcess;
    if (current) {
      myTopFrame = calcTopFrame();
    }
  }

  @NotNull
  public ThreadReferenceProxyImpl getThreadProxy() {
    return myThreadProxy;
  }

  private JavaStackFrame calcTopFrame() {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    try {
      StackFrameProxyImpl frame = myThreadProxy.frame(0);
      if (frame != null) {
        return new JavaStackFrame(frame, myDebugProcess, myTracker);
      }
    }
    catch (EvaluateException e) {
      e.printStackTrace();
    }
    finally {
      myTopFrameReady = true;
    }
    return null;
  }

  @Nullable
  @Override
  public JavaStackFrame getTopFrame() {
    if (!myTopFrameReady) {
      //TODO: remove sync calculation
      if (DebuggerManagerThreadImpl.isManagerThread()) {
        myTopFrame = calcTopFrame();
      }
      else {
        myDebugProcess.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
          @Override
          protected void action() throws Exception {
            myTopFrame = calcTopFrame();
          }
        });
      }
    }
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(final int firstFrameIndex, final XStackFrameContainer container) {
    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        boolean showLibraryStackframes = DebuggerSettings.getInstance().SHOW_LIBRARY_STACKFRAMES;
        List<JavaStackFrame> frames = new ArrayList<JavaStackFrame>();
        if (!myThreadProxy.isCollected() && myDebugProcess.getSuspendManager().isSuspended(myThreadProxy)) {
          int status = myThreadProxy.status();
          if (!(status == ThreadReference.THREAD_STATUS_UNKNOWN) &&
              !(status == ThreadReference.THREAD_STATUS_NOT_STARTED) &&
              !(status == ThreadReference.THREAD_STATUS_ZOMBIE)) {
            try {
              int framesToSkip = firstFrameIndex;
              boolean first = true;
              for (StackFrameProxyImpl stackFrame : myThreadProxy.frames()) {
                if (first && framesToSkip > 0) {
                  framesToSkip--;
                  first = false;
                  continue;
                }
                JavaStackFrame frame = new JavaStackFrame(stackFrame, myDebugProcess, myTracker);
                if (showLibraryStackframes || (!frame.getDescriptor().isSynthetic() && !frame.getDescriptor().isInLibraryContent())) {
                  if (framesToSkip > 0) {
                    framesToSkip--;
                    continue;
                  }
                  frames.add(frame);
                }
              }
            }
            catch (EvaluateException e) {
              container.errorOccurred(e.getMessage());
              return;
            }
          }
        }
        container.addStackFrames(frames, true);
      }
    });
  }

  private static String calcRepresentation(ThreadReferenceProxyImpl thread) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    String name = thread.name();
    ThreadGroupReferenceProxyImpl gr = thread.threadGroupProxy();
    final String grname = (gr != null)? gr.name() : null;
    final String threadStatusText = DebuggerUtilsEx.getThreadStatusText(thread.status());
    //noinspection HardCodedStringLiteral
    if (grname != null && !"SYSTEM".equalsIgnoreCase(grname)) {
      return DebuggerBundle.message("label.thread.node.in.group", name, thread.uniqueID(), threadStatusText, grname);
    }
    return DebuggerBundle.message("label.thread.node", name, thread.uniqueID(), threadStatusText);
  }
}
