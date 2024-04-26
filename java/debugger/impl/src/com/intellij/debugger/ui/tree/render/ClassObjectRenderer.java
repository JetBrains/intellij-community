// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

final class ClassObjectRenderer extends CompoundRendererProvider {
  private static final Logger LOG = Logger.getInstance(ClassObjectRenderer.class);

  @Override
  protected String getName() {
    return "Class";
  }

  @Override
  protected String getClassName() {
    return "java.lang.Class";
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) ->
      new JavaValue.JavaFullValueEvaluator(JavaDebuggerBundle.message("message.node.navigate"), evaluationContext) {
        @Override
        public void evaluate(@NotNull XFullValueEvaluationCallback callback) {
          Value value = valueDescriptor.getValue();
          ClassType type = ((ClassType)value.type());
          Method nameMethod = DebuggerUtils.findMethod(type, "getName", "()Ljava/lang/String;");
          if (nameMethod != null) {
            try {
              final DebugProcessImpl process = evaluationContext.getDebugProcess();
              Value res = process.invokeMethod(evaluationContext, (ObjectReference)value, nameMethod, Collections.emptyList());
              if (res instanceof StringReference) {
                callback.evaluated("");
                final String line = ((StringReference)res).value();
                ApplicationManager.getApplication().runReadAction(() -> {
                  final PsiClass psiClass = DebuggerUtils.findClass(line,
                                                                    valueDescriptor.getProject(),
                                                                    process.getSearchScope());
                  if (psiClass != null) {
                    DebuggerUIUtil.invokeLater(() -> psiClass.navigate(true));
                  }
                });
              }
            }
            catch (EvaluateException e) {
              LOG.info("Exception while getting type name", e);
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