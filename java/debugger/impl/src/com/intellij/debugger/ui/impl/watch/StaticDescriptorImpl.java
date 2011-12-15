/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
    final ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
    return getName() + " = " + classRenderer.renderTypeName(myType.name());
  }
}