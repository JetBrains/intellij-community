/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.codeinsight;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.EditorEvaluationCommand;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.sun.jdi.ClassType;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class RuntimeTypeEvaluator extends EditorEvaluationCommand<PsiType> {
  public RuntimeTypeEvaluator(@Nullable Editor editor, PsiElement expression, DebuggerContextImpl context, final ProgressIndicator indicator) {
    super(editor, expression, context, indicator);
  }

  public void threadAction() {
    PsiType type = null;
    try {
      type = evaluate();
    }
    catch (ProcessCanceledException ignored) {
    }
    catch (EvaluateException ignored) {
    }
    finally {
      typeCalculationFinished(type);
    }
  }

  protected abstract void typeCalculationFinished(@Nullable PsiType type);

  @Nullable
  protected PsiType evaluate(final EvaluationContextImpl evaluationContext) throws EvaluateException {
    final Project project = evaluationContext.getProject();

    ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
      public ExpressionEvaluator compute() throws EvaluateException {
        return EvaluatorBuilderImpl.getInstance().build(myElement, ContextUtil.getSourcePosition(evaluationContext));
      }
    });

    final Value value = evaluator.evaluate(evaluationContext);
    if(value != null){
      return getCastableRuntimeType(project, value);
    }

    throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.surrounded.expression.null"));
  }

  @Nullable
  public static PsiType getCastableRuntimeType(Project project, Value value) {
    Type type = value.type();
    PsiType psiType = findPsiType(project, type);
    if (psiType != null) {
      return psiType;
    }

    if (type instanceof ClassType) {
      ClassType superclass = ((ClassType)type).superclass();
      if (superclass != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(superclass.name())) {
        psiType = findPsiType(project, superclass);
        if (psiType != null) {
          return psiType;
        }
      }

      for (InterfaceType interfaceType : ((ClassType)type).interfaces()) {
        psiType = findPsiType(project, interfaceType);
        if (psiType != null) {
          return psiType;
        }
      }
    }
    return null;
  }

  private static PsiType findPsiType(Project project, Type type) {
    AccessToken token = ReadAction.start();
    try {
      return DebuggerUtils.getType(type.name().replace('$', '.'), project);
    }
    finally {
      token.finish();
    }
  }

  public static boolean isSubtypeable(PsiExpression expr) {
    final PsiType type = expr.getType();
    if (type instanceof PsiPrimitiveType) {
      return false;
    }
    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
        return false;
      }
    }
    return true;
  }
}
