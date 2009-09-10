package com.intellij.debugger.codeinsight;

import com.intellij.debugger.ui.EditorEvaluationCommand;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class RuntimeTypeEvaluator extends EditorEvaluationCommand<String> {
  public RuntimeTypeEvaluator(@Nullable Editor editor, PsiElement expression, DebuggerContextImpl context, final ProgressIndicator indicator) {
    super(editor, expression, context, indicator);
  }

  public void threadAction() {
    String type = null;
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

  protected abstract void typeCalculationFinished(@Nullable String type);

  protected String evaluate(final EvaluationContextImpl evaluationContext) throws EvaluateException {
    final Project project = evaluationContext.getProject();

    ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(project, new EvaluatingComputable<ExpressionEvaluator>() {
      public ExpressionEvaluator compute() throws EvaluateException {
        return EvaluatorBuilderImpl.getInstance().build(myElement, ContextUtil.getSourcePosition(evaluationContext));
      }
    });

    final Value value = evaluator.evaluate(evaluationContext);
    if(value != null){
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        public String compute() {
          return DebuggerUtilsEx.getQualifiedClassName(value.type().name(), project);
        }
      });
    }
    else {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.surrounded.expression.null"));
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
