package org.jetbrains.debugger.memory.utils;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstanceJavaValue extends JavaValue {
  public InstanceJavaValue(JavaValue javaValue,
                              @NotNull ValueDescriptorImpl valueDescriptor,
                              @NotNull EvaluationContextImpl evaluationContext,
                              NodeManagerImpl nodeManager,
                              boolean contextSet) {
    super(javaValue, valueDescriptor, evaluationContext, nodeManager, contextSet);
  }

  @Nullable
  @Override
  public String getEvaluationExpression() {
    ObjectReference ref = ((ObjectReference)getDescriptor().getValue());
    return NamesUtils.getUniqueName(ref);
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }
}
