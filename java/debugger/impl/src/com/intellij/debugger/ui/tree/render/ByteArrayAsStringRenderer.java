// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Value;

/**
 * @author egor
 */
public class ByteArrayAsStringRenderer extends CompoundReferenceRenderer {
  public ByteArrayAsStringRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "String", null, null);
    setClassName("byte[]");
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
              return new String(bytes);
            }
          }
        }
        return super.calcLabel(descriptor, evaluationContext, labelListener);
      }
    };
    labelRenderer.setLabelExpression(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "new String(this)"));
    setLabelRenderer(labelRenderer);
  }
}
