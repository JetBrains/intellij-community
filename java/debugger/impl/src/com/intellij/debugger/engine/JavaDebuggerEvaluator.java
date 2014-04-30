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

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author egor
 */
public class JavaDebuggerEvaluator extends XDebuggerEvaluator {
  private final DebugProcessImpl myDebugProcess;
  private final DebuggerContextImpl myDebuggerContext;

  public JavaDebuggerEvaluator(DebugProcessImpl debugProcess, DebuggerContextImpl context) {
    myDebugProcess = debugProcess;
    myDebuggerContext = context;
  }

  @Override
  public void evaluate(@NotNull final String expression,
                       @NotNull final XEvaluationCallback callback,
                       @Nullable XSourcePosition expressionPosition) {
    evaluate(XExpressionImpl.fromText(expression), callback, expressionPosition);
  }

  @Override
  public void evaluate(@NotNull final XExpression expression,
                       @NotNull final XEvaluationCallback callback,
                       @Nullable XSourcePosition expressionPosition) {
    myDebugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        WatchItemDescriptor descriptor = new WatchItemDescriptor(myDebugProcess.getProject(), TextWithImportsImpl.fromXExpression(
          expression));
        EvaluationContextImpl evalContext = myDebuggerContext.createEvaluationContext();
        descriptor.setContext(evalContext);
        descriptor.updateRepresentation(evalContext, DescriptorLabelListener.DUMMY_LISTENER);
        callback.evaluated(new JavaValue(descriptor, evalContext, myDebugProcess.getXdebugProcess().getNodeManager()));
      }
    });
  }
}
