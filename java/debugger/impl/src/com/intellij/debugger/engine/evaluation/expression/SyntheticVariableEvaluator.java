// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.JVMName;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

/**
 * @author lex
 */
public class SyntheticVariableEvaluator implements Evaluator{
  private static final Logger LOG = Logger.getInstance(SyntheticVariableEvaluator.class);

  private final CodeFragmentEvaluator myCodeFragmentEvaluator;
  private final String myLocalName;
  private final JVMName myTypeName;
  private String myTypeNameString = null;

  public SyntheticVariableEvaluator(CodeFragmentEvaluator codeFragmentEvaluator, String localName, @Nullable JVMName typeName) {
    myCodeFragmentEvaluator = codeFragmentEvaluator;
    myLocalName = localName;
    myTypeName = typeName;
  }

  @Override
  public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
    if (myTypeNameString == null && myTypeName != null) {
      myTypeNameString = myTypeName.getName(context.getDebugProcess());
    }
    return myCodeFragmentEvaluator.getValue(myLocalName, context.getDebugProcess().getVirtualMachineProxy());
  }

  @Override
  public Modifier getModifier() {
    return new Modifier() {
      @Override
      public boolean canInspect() {
        return false;
      }

      @Override
      public boolean canSetValue() {
        return false;
      }

      @Override
      public void setValue(Value value) throws EvaluateException {
        if (value != null) {
          Type type = value.type();
          if (myTypeNameString != null && !DebuggerUtilsEx.isAssignableFrom(myTypeNameString, type)) {
            throw EvaluateExceptionUtil.createEvaluateException(
              DebuggerBundle.message("evaluation.error.cannot.cast.object", type.name(), myTypeNameString));
          }
        }
        myCodeFragmentEvaluator.setValue(myLocalName, value);
      }

      @Override
      public Type getExpectedType() {
        LOG.assertTrue(false);
        return null;
      }

      @Override
      public NodeDescriptorImpl getInspectItem(Project project) {
        return null;
      }
    };
  }
}
