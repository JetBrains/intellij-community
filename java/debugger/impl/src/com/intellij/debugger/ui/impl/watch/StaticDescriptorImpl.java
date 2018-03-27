/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

/*
 * Class StaticDescriptorImpl
 * @author Jeka
 */
package com.intellij.debugger.ui.impl.watch;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.StaticDescriptor;
import com.intellij.debugger.ui.tree.render.ClassRenderer;
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.TypeComponent;

public class StaticDescriptorImpl extends NodeDescriptorImpl implements StaticDescriptor{

  private final ReferenceType myType;
  private final boolean myHasStaticFields;

  public StaticDescriptorImpl(ReferenceType refType) {
    myType = refType;
    myHasStaticFields = myType.allFields().stream().anyMatch(TypeComponent::isStatic);
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
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    return getName() + " = " + classRenderer.renderTypeName(myType.name());
  }
}