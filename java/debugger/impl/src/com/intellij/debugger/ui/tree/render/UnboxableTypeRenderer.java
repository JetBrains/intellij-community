/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;

/**
 * @author egor
 */
public abstract class UnboxableTypeRenderer extends CompoundReferenceRenderer {
  public UnboxableTypeRenderer(String className, NodeRendererSettings rendererSettings) {
    super(rendererSettings, StringUtil.getShortName(className), new LabelRenderer() {
      @Override
      public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
        throws EvaluateException {
        return DebuggerUtils.getValueAsString(evaluationContext, UnBoxingEvaluator.getInnerPrimitiveValue((ObjectReference)descriptor.getValue()));
      }

      @Override
      public boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
        return false;
      }
    }, null);
    LOG.assertTrue(UnBoxingEvaluator.isTypeUnboxable(className));
    setClassName(className);
    setEnabled(true);
  }

  @Override
  public boolean isApplicable(Type type) {
    return type instanceof ReferenceType && StringUtil.equals(type.name(), getClassName());
  }

  public static class BooleanRenderer extends UnboxableTypeRenderer {
    public BooleanRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_BOOLEAN, rendererSettings);
    }
  }

  public static class ByteRenderer extends UnboxableTypeRenderer {
    public ByteRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_BYTE, rendererSettings);
    }
  }

  public static class CharacterRenderer extends UnboxableTypeRenderer {
    public CharacterRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_CHARACTER, rendererSettings);
    }
  }

  public static class ShortRenderer extends UnboxableTypeRenderer {
    public ShortRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_SHORT, rendererSettings);
    }
  }

  public static class IntegerRenderer extends UnboxableTypeRenderer {
    public IntegerRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_INTEGER, rendererSettings);
    }
  }

  public static class LongRenderer extends UnboxableTypeRenderer {
    public LongRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_LONG, rendererSettings);
    }
  }

  public static class FloatRenderer extends UnboxableTypeRenderer {
    public FloatRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_FLOAT, rendererSettings);
    }
  }

  public static class DoubleRenderer extends UnboxableTypeRenderer {
    public DoubleRenderer(NodeRendererSettings rendererSettings) {
      super(CommonClassNames.JAVA_LANG_DOUBLE, rendererSettings);
    }
  }
}
