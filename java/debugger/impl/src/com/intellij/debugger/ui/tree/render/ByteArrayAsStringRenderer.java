// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;

import java.nio.charset.StandardCharsets;

final class ByteArrayAsStringRenderer extends CompoundRendererProvider {
  @Override
  protected String getName() {
    return "String";
  }

  @Override
  protected String getClassName() {
    return "byte[]";
  }

  @Override
  protected ValueLabelRenderer getValueLabelRenderer() {
    LabelRenderer labelRenderer = new LabelRenderer() {
      @Override
      public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
        throws EvaluateException {
        if (evaluationContext instanceof EvaluationContextImpl && !((EvaluationContextImpl)evaluationContext).isEvaluationPossible()) {
          Value value = descriptor.getValue();
          if (value instanceof ArrayReference) {
            // TODO: read charset from the target vm
            byte[] bytes = DebuggerUtilsImpl.readBytesArray(value);
            if (bytes != null) {
              return new String(bytes, StandardCharsets.UTF_8);
            }
          }
        }
        return super.calcLabel(descriptor, evaluationContext, labelListener);
      }
    };
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "new String(this)"));
    return labelRenderer;
  }
}
