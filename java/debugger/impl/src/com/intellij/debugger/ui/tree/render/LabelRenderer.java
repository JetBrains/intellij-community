package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.Value;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * User: lex
 * Date: Sep 20, 2003
 * Time: 10:27:12 PM
 */
public class LabelRenderer extends com.intellij.debugger.ui.tree.render.ReferenceRenderer implements ValueLabelRenderer{
  public static final @NonNls String UNIQUE_ID = "LabelRenderer";
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.impl.watch.render.ClassLabelRenderer");

  private final CachedEvaluator myLabelExpression = new CachedEvaluator() {
    protected String getClassName() {
      return LabelRenderer.this.getClassName();
    }
  };

  public LabelRenderer() {
    super();
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public LabelRenderer clone() {
    return (LabelRenderer)super.clone();
  }

  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
    throws EvaluateException {
    String result;
    LOG.assertTrue(!(descriptor instanceof PrimitiveValue));

    final DebugProcess debugProcess = evaluationContext.getDebugProcess();
    final Value value = descriptor.getValue();
    if (value != null) {
      try {
        final ExpressionEvaluator evaluator = myLabelExpression.getEvaluator(debugProcess.getProject());

        if(!debugProcess.isAttached()) {
          throw EvaluateExceptionUtil.PROCESS_EXITED;
        }
        EvaluationContext thisEvaluationContext = evaluationContext.createEvaluationContext(value);
        Value labelValue = evaluator.evaluate(thisEvaluationContext);
        result = DebuggerUtils.getValueAsString(thisEvaluationContext, labelValue);
      }
      catch (final EvaluateException ex) {
        throw new EvaluateException(DebuggerBundle.message("error.unable.to.evaluate.expression") + " " + ex.getMessage(), ex);
      }
    }
    else {
      //noinspection HardCodedStringLiteral
      result = "null";
    }
    return result;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
    TextWithImports labelExpression = DebuggerUtils.getInstance().readTextWithImports(element, "LABEL_EXPRESSION");
    if (labelExpression != null) {
      setLabelExpression(labelExpression);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    DebuggerUtils.getInstance().writeTextWithImports(element, "LABEL_EXPRESSION", getLabelExpression());
  }

  public TextWithImports getLabelExpression() {
    return myLabelExpression.getReferenceExpression();
  }

  public void setLabelExpression(TextWithImports expression) {
    myLabelExpression.setReferenceExpression(expression);
  }

}
