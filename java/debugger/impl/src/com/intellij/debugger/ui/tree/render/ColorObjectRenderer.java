// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

class ColorObjectRenderer extends CompoundReferenceRenderer {
  ColorObjectRenderer() {
    super("Color", null, null);
    setClassName("java.awt.Color");
    setEnabled(true);
  }

  @Override
  public Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
    Value value = descriptor.getValue();
    if (value instanceof ObjectReference) {
      try {
        ObjectReference objRef = (ObjectReference)value;
        ReferenceType refType = objRef.referenceType();
        if (refType instanceof ClassType) {
          Value rgbValue = null;
          Method getRGBMethod = DebuggerUtils.findMethod(refType, "getRGB", "()I");
          if (getRGBMethod != null) {
            ReferenceType rgbMethodDeclaringType = getRGBMethod.declaringType();
            if (rgbMethodDeclaringType.name().equals(getClassName())) { // getRGB is not overridden
              Field valueField = rgbMethodDeclaringType.fieldByName("value");
              if (valueField != null) {
                rgbValue = objRef.getValue(valueField);
              }
            }
            if (rgbValue instanceof IntegerValue) {
              return createIcon((IntegerValue)rgbValue);
            }
            else {
              EvaluationContextImpl evalContext = ((EvaluationContextImpl)evaluationContext);
              DebugProcessImpl debugProcess = evalContext.getDebugProcess();
              debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
                @Override
                public void contextAction(@NotNull SuspendContextImpl suspendContext) {
                  try {
                    Value rgbValue = debugProcess.invokeMethod(evaluationContext, objRef, getRGBMethod, Collections.emptyList());
                    if (rgbValue instanceof IntegerValue) {
                      descriptor.setValueIcon(createIcon((IntegerValue)rgbValue));
                      listener.labelChanged();
                    }
                  }
                  catch (EvaluateException e) {
                    LOG.info(e);
                  }
                }
              });
            }
          }
        }
      }
      catch (Exception e) {
        throw new EvaluateException(e.getMessage(), e);
      }
    }
    return null;
  }

  private static Icon createIcon(IntegerValue rgbValue) {
    //noinspection UseJBColor
    return JBUI.scale(new ColorIcon(16, 12, new Color(rgbValue.value(), true), true));
  }
}
