// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
* @author egor
*/
class ClassObjectRenderer extends CompoundReferenceRenderer implements FullValueEvaluatorProvider {
  private static final Logger LOG = Logger.getInstance(ClassObjectRenderer.class);

  ClassObjectRenderer() {
    super("Class", null, null);
    setClassName("java.lang.Class");
    setEnabled(true);
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(final EvaluationContextImpl evaluationContext,
                                                   final ValueDescriptorImpl valueDescriptor) {
    return new JavaValue.JavaFullValueEvaluator(DebuggerBundle.message("message.node.navigate"), evaluationContext) {
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