// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
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
  public UnboxableTypeRenderer(String className) {
    super(StringUtil.getShortName(className), new LabelRenderer() {
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
    public BooleanRenderer() {
      super(CommonClassNames.JAVA_LANG_BOOLEAN);
    }
  }

  public static class ByteRenderer extends UnboxableTypeRenderer {
    public ByteRenderer() {
      super(CommonClassNames.JAVA_LANG_BYTE);
    }
  }

  public static class CharacterRenderer extends UnboxableTypeRenderer {
    public CharacterRenderer() {
      super(CommonClassNames.JAVA_LANG_CHARACTER);
    }
  }

  public static class ShortRenderer extends UnboxableTypeRenderer {
    public ShortRenderer() {
      super(CommonClassNames.JAVA_LANG_SHORT);
    }
  }

  public static class IntegerRenderer extends UnboxableTypeRenderer {
    public IntegerRenderer() {
      super(CommonClassNames.JAVA_LANG_INTEGER);
    }
  }

  public static class LongRenderer extends UnboxableTypeRenderer {
    public LongRenderer() {
      super(CommonClassNames.JAVA_LANG_LONG);
    }
  }

  public static class FloatRenderer extends UnboxableTypeRenderer {
    public FloatRenderer() {
      super(CommonClassNames.JAVA_LANG_FLOAT);
    }
  }

  public static class DoubleRenderer extends UnboxableTypeRenderer {
    public DoubleRenderer() {
      super(CommonClassNames.JAVA_LANG_DOUBLE);
    }
  }
}
