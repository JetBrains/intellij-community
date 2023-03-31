// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.expression.UnBoxingEvaluator;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public abstract class UnboxableTypeRenderer extends CompoundRendererProvider {
  private static final Logger LOG = Logger.getInstance(UnboxableTypeRenderer.class);
  private final String myClassName;

  protected UnboxableTypeRenderer(String className) {
    LOG.assertTrue(UnBoxingEvaluator.isTypeUnboxable(className));
    myClassName = className;
  }

  @Override
  protected String getName() {
    return StringUtil.getShortName(myClassName);
  }

  @Override
  protected ValueLabelRenderer getValueLabelRenderer() {
    return new LabelRenderer() {
      @Override
      public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener)
        throws EvaluateException {
        CompletableFuture<PrimitiveValue> future =
          UnBoxingEvaluator.getInnerPrimitiveValue((ObjectReference)descriptor.getValue(), false);
        if (future.isDone()) {
          return DebuggerUtils.getValueAsString(evaluationContext, future.join());
        }
        return future.handle((r, ex) -> {
          String res = "";
          if (ex == null) {
            try {
              res = DebuggerUtils.getValueAsString(evaluationContext, r);
              descriptor.setValueLabel(res);
            }
            catch (EvaluateException e) {
              descriptor.setValueLabelFailed(e);
            }
          }
          else {
            descriptor.setValueLabelFailed(new EvaluateException(null, ex));
          }
          labelListener.labelChanged();
          return res;
        }).getNow(XDebuggerUIConstants.getCollectingDataMessage());
      }

      @Override
      public boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
        return false;
      }
    };
  }

  @Override
  protected Function<Type, CompletableFuture<Boolean>> getIsApplicableChecker() {
    return type -> CompletableFuture.completedFuture(type instanceof ReferenceType && StringUtil.equals(type.name(), myClassName));
  }

  @Override
  protected boolean isEnabled() {
    return true;
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
