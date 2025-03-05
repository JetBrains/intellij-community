// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.ColorIcon;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

final class ColorObjectRenderer extends CompoundRendererProvider {
  private static final Logger LOG = Logger.getInstance(ColorObjectRenderer.class);

  @Override
  protected String getName() {
    return "Color";
  }

  @Override
  protected String getClassName() {
    return "java.awt.Color";
  }

  @Override
  protected ValueIconRenderer getIconRenderer() {
    return (descriptor, evaluationContext, listener) -> {
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
              if (rgbMethodDeclaringType.name().equals("java.awt.Color")) { // getRGB is not overridden
                Field valueField = DebuggerUtils.findField(rgbMethodDeclaringType, "value");
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
                evalContext.getManagerThread().schedule(new SuspendContextCommandImpl(evalContext.getSuspendContext()) {
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
    };
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  private static Icon createIcon(IntegerValue rgbValue) {
    //noinspection UseJBColor
    return JBUIScale.scaleIcon(new ColorIcon(16, 12, new Color(rgbValue.value(), true), true));
  }
}
