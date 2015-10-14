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
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;

/**
 * @author egor
 */
public class BinaryRenderer extends NodeRendererImpl {
  private static final Logger LOG = Logger.getInstance(BinaryRenderer.class);

  @Override
  public String calcLabel(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    Value value = valueDescriptor.getValue();
    StringBuilder buf = new StringBuilder();

    if (value == null) {
      return "null";
    }
    else {
      String prefix = "0b";
      buf.append(prefix);
      if (value instanceof LongValue) {
        buf.append(Long.toBinaryString(((LongValue)value).longValue()));
      }
      else if (value instanceof PrimitiveValue) {
        buf.append(Integer.toBinaryString(((PrimitiveValue)value).intValue()));
      }
      else {
        LOG.assertTrue(false);
      }
      // group by 8
      for (int i = buf.length() - 8; i > prefix.length(); i -= 8) {
        buf.insert(i, '_');
      }
      return buf.toString();
    }
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {}

  @Override
  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return null;
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return false;
  }

  @Override
  public String getName() {
    return "Binary";
  }

  @Override
  public String getUniqueId() {
    return "BinaryRenderer";
  }

  @Override
  public boolean isApplicable(Type t) {
    if (t == null) {
      return false;
    }
    return t instanceof ByteType ||
           t instanceof ShortType ||
           t instanceof IntegerType ||
           t instanceof LongType;
  }
}
