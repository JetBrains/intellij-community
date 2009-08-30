/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.ui.tree.StaticDescriptor;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.sun.jdi.Field;
import com.sun.jdi.ReferenceType;

public class StaticDescriptorImpl extends NodeDescriptorImpl implements StaticDescriptor{

  private final ReferenceType myType;
  private final boolean myHasStaticFields;

  public StaticDescriptorImpl(ReferenceType refType) {
    myType = refType;

    boolean hasStaticFields = false;
    for (Field field : myType.allFields()) {
      if (field.isStatic()) {
        hasStaticFields = true;
        break;
      }
    }
    myHasStaticFields = hasStaticFields;
  }

  public ReferenceType getType() {
    return myType;
  }

  public String getName() {
    //noinspection HardCodedStringLiteral
    return "static";
  }

  public boolean isExpandable() {
    return myHasStaticFields;
  }

  public void setContext(EvaluationContextImpl context) {
  }

  protected String calcRepresentation(EvaluationContextImpl context, DescriptorLabelListener descriptorLabelListener) throws EvaluateException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getName() + " = " + myType.name();
  }
}