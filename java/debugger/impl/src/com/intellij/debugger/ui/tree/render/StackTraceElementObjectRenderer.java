// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.execution.filters.ExceptionFilter;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

final class StackTraceElementObjectRenderer extends CompoundRendererProvider {
  private static final Logger LOG = Logger.getInstance(StackTraceElementObjectRenderer.class);

  @Override
  protected String getName() {
    return "StackTraceElement";
  }

  @Override
  protected String getClassName() {
    return "java.lang.StackTraceElement";
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) -> new JavaValue.JavaFullValueEvaluator(JavaDebuggerBundle.message("message.node.navigate"),
                                                                                        evaluationContext) {
      @Override
      public void evaluate(@NotNull XFullValueEvaluationCallback callback) {
        Value value = valueDescriptor.getValue();
        ClassType type = ((ClassType)value.type());
        Method toString = DebuggerUtils.findMethod(type, "toString", "()Ljava/lang/String;");
        if (toString != null) {
          try {
            Value res =
              evaluationContext.getDebugProcess()
                .invokeMethod(evaluationContext, (ObjectReference)value, toString, Collections.emptyList());
            if (res instanceof StringReference) {
              callback.evaluated("");
              final String line = ((StringReference)res).value();
              ApplicationManager.getApplication().runReadAction(() -> {
                Project project = valueDescriptor.getProject();
                ExceptionFilter filter = new ExceptionFilter(project, evaluationContext.getDebugProcess().getSession().getSearchScope());
                Filter.Result result = filter.applyFilter(line, line.length());
                if (result != null) {
                  final HyperlinkInfo info = result.getFirstHyperlinkInfo();
                  if (info != null) {
                    DebuggerUIUtil.invokeLater(() -> info.navigate(project));
                  }
                }
              });
            }
          }
          catch (EvaluateException e) {
            LOG.info("Exception while getting stack info", e);
          }
        }
      }

      @Override
      public boolean isShowValuePopup() {
        return false;
      }
    };
  }
}
