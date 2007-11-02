/*
 * Class TypeEvaluator
 * @author Jeka
 */
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.sun.jdi.ReferenceType;

public class TypeEvaluator implements Evaluator {
  private JVMName myTypeName;

  public TypeEvaluator(JVMName typeName) {
    myTypeName = typeName;
  }

  public Modifier getModifier() {
    return null;
  }

  /**
   * @return ReferenceType in the target VM, with the given fully qualified name
   */
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    String typeName = myTypeName.getName(debugProcess);
    final ReferenceType type = debugProcess.findClass(context, typeName, context.getClassLoader());
    if (type == null) {
      throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.class.not.loaded", typeName));
    }
    return type;
  }
}
