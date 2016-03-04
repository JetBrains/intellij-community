/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
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
class ClassObjectRenderer extends ToStringBasedRenderer implements FullValueEvaluatorProvider {
  private static final Logger LOG = Logger.getInstance(ClassObjectRenderer.class);

  public ClassObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "Class", null, null);
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
        Method nameMethod = type.concreteMethodByName("getName", "()Ljava/lang/String;");
        if (nameMethod != null) {
          try {
            final DebugProcessImpl process = evaluationContext.getDebugProcess();
            Value res = process.invokeMethod(evaluationContext, (ObjectReference)value, nameMethod, Collections.emptyList());
            if (res instanceof StringReference) {
              callback.evaluated("");
              final String line = ((StringReference)res).value();
              ApplicationManager.getApplication().runReadAction(new Runnable() {
                @Override
                public void run() {
                  final PsiClass psiClass = DebuggerUtils.findClass(line,
                                                                    valueDescriptor.getProject(),
                                                                    process.getSearchScope());
                  if (psiClass != null) {
                    DebuggerUIUtil.invokeLater(new Runnable() {
                      @Override
                      public void run() {
                        psiClass.navigate(true);
                      }
                    });
                  }
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