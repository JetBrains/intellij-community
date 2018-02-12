/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.memory.filtering;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.render.CachedEvaluator;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XExpression;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vitaliy.Bibaev
 */
public class ConditionCheckerImpl implements ConditionChecker {
  private final DebugProcessImpl myDebugProcess;
  private final MyCachedEvaluator myEvaluator;
  private final AtomicReference<CheckingResult> myResultReference = new AtomicReference<>();

  public ConditionCheckerImpl(@NotNull DebugProcessImpl debugProcess,
                              @Nullable XExpression expression,
                              @NotNull String className) {
    myDebugProcess = debugProcess;
    myEvaluator = new MyCachedEvaluator(myDebugProcess.getProject(), className);
    myEvaluator.setReferenceExpression(TextWithImportsImpl.fromXExpression(expression));
  }

  @Override
  public CheckingResult check(@NotNull Value ref) {
    myDebugProcess.getManagerThread().invokeAndWait(new MyCheckerCommand(ref));
    return myResultReference.get();
  }

  private class MyCheckerCommand extends DebuggerContextCommandImpl {
    private final Value myReference;

    protected MyCheckerCommand(@NotNull Value ref) {
      super(myDebugProcess.getDebuggerContext());
      myReference = ref;
    }

    @Override
    public Priority getPriority() {
      return Priority.LOWEST;
    }

    @Override
    public void threadAction(@NotNull SuspendContextImpl suspendContext) {
      try {
        EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy(), myReference);
        myResultReference.set(DebuggerUtilsEx.evaluateBoolean(myEvaluator.getEvaluator(), evaluationContext)
                              ? CheckingResultImpl.SUCCESS
                              : CheckingResultImpl.FAIL);
      }
      catch (EvaluateException e) {
        myResultReference.set(CheckingResultImpl.error(e.getMessage()));
      }
    }
  }

  private static class MyCachedEvaluator extends CachedEvaluator {
    private final Project myProject;
    private final String myClassName;

    public MyCachedEvaluator(@NotNull Project project, @NotNull String className) {
      super();
      myProject = project;
      myClassName = className;
    }

    ExpressionEvaluator getEvaluator() throws EvaluateException {
      return getEvaluator(myProject);
    }

    @Override
    protected String getClassName() {
      return myClassName;
    }
  }
}
