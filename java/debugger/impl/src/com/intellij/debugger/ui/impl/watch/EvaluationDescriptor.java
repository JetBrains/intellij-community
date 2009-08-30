package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.EvaluatingComputable;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 12, 2004
 * Time: 5:24:17 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class EvaluationDescriptor extends ValueDescriptorImpl{
  private Modifier myModifier;
  protected TextWithImports myText;
  private CodeFragmentFactory myCodeFragmentFactory = null; // used to force specific context, e.g. from evaluation

  protected EvaluationDescriptor(TextWithImports text, Project project, Value value) {
    super(project, value);
    myText = text;
  }

  protected EvaluationDescriptor(TextWithImports text, Project project) {
    super(project);
    setLvalue(false);
    myText = text;
  }

  public final void setCodeFragmentFactory(CodeFragmentFactory codeFragmentFactory) {
    myCodeFragmentFactory = codeFragmentFactory;
  }

  public final @Nullable CodeFragmentFactory getCodeFragmentFactory() {
    return myCodeFragmentFactory;
  }

  protected final @NotNull CodeFragmentFactory getEffectiveCodeFragmentFactory(final PsiElement psiContext) {
    if (myCodeFragmentFactory != null) {
      return myCodeFragmentFactory;
    }
    return ApplicationManager.getApplication().runReadAction(new Computable<CodeFragmentFactory>() {
      public CodeFragmentFactory compute() {
        final List<CodeFragmentFactory> codeFragmentFactories = DebuggerUtilsEx.getCodeFragmentFactories(psiContext);
        // the list always contains at least DefaultCodeFragmentFactory
        return codeFragmentFactories.get(0);
      }
    });
  }

  protected abstract EvaluationContextImpl getEvaluationContext (EvaluationContextImpl evaluationContext);

  protected abstract PsiCodeFragment getEvaluationCode(StackFrameContext context) throws EvaluateException;

  public final Value calcValue(EvaluationContextImpl evaluationContext) throws EvaluateException {
    try {
      final EvaluationContextImpl thisEvaluationContext = getEvaluationContext(evaluationContext);

      final ExpressionEvaluator evaluator = DebuggerInvocationUtil.commitAndRunReadAction(myProject, new EvaluatingComputable<ExpressionEvaluator>() {
        public ExpressionEvaluator compute() throws EvaluateException {
          return EvaluatorBuilderImpl.getInstance().build(getEvaluationCode(thisEvaluationContext), ContextUtil.getSourcePosition(thisEvaluationContext));
        }
      });


      if (!thisEvaluationContext.getDebugProcess().isAttached()) {
        throw EvaluateExceptionUtil.PROCESS_EXITED;
      }
      StackFrameProxyImpl frameProxy = thisEvaluationContext.getFrameProxy();
      if (frameProxy == null) {
        throw EvaluateExceptionUtil.NULL_STACK_FRAME;
      }

      final Value value = evaluator.evaluate(thisEvaluationContext);
      if (value instanceof ObjectReference) {
        thisEvaluationContext.getSuspendContext().keep(((ObjectReference)value));
      }
      myModifier = evaluator.getModifier();
      setLvalue(myModifier != null);

      return value;
    }
    catch (final EvaluateException ex) {
      throw new EvaluateException(ex.getLocalizedMessage(), ex);
    }
  }

  public String calcValueName() {
    return getName();
  }

  public PsiExpression getDescriptorEvaluation(DebuggerContext context) throws EvaluateException {
    PsiElement evaluationCode = getEvaluationCode(context);
    if(evaluationCode instanceof PsiExpressionCodeFragment) {
      return ((PsiExpressionCodeFragment)evaluationCode).getExpression();
    }
    else {
      throw new EvaluateException(DebuggerBundle.message("error.cannot.create.expression.from.code.fragment"), null);
    }
  }

  public Modifier getModifier() {
    return myModifier;
  }

  public boolean canSetValue() {
    return super.canSetValue() && myModifier != null && myModifier.canSetValue();
  }

  public TextWithImports getEvaluationText() {
    return myText;
  }
}
