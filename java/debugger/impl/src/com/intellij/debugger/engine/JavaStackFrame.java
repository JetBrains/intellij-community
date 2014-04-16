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

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.sun.jdi.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaStackFrame extends XStackFrame {
  private final StackFrameProxyImpl myStackFrameProxy;
  private final DebugProcessImpl myDebugProcess;
  private final XSourcePosition mySourcePosition;

  public JavaStackFrame(StackFrameProxyImpl stackFrameProxy, DebugProcessImpl debugProcess) {
    myStackFrameProxy = stackFrameProxy;
    myDebugProcess = debugProcess;
    mySourcePosition = calcSourcePosition();
  }

  private final XSourcePosition calcSourcePosition() {
    final CompoundPositionManager positionManager = myDebugProcess.getPositionManager();
    if (positionManager == null) {
      // process already closed
      return null;
    }
    Location location = null;
    try {
      location = myStackFrameProxy.location();
    }
    catch (Throwable e) {
      //TODO: handle
    }
    SourcePosition position = positionManager.getSourcePosition(location);
    if (position != null) {
      return DebuggerUtilsEx.toXSourcePosition(position);
    }
    return null;
  }

  @Nullable
  @Override
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  @Override
  public void computeChildren(@NotNull XCompositeNode node) {
    super.computeChildren(node);
  }
}
