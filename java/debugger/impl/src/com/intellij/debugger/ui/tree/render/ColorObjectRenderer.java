// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.sun.jdi.*;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

class ColorObjectRenderer extends CompoundReferenceRenderer {
  ColorObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "Color", null, null);
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
          Method getRGBMethod = ((ClassType)refType).concreteMethodByName("getRGB", "()I");
          if (getRGBMethod == null || getRGBMethod.declaringType().name().equals(getClassName())) { // getRGB is not overridden
            Field valueField = refType.fieldByName("value");
            if (valueField != null) {
              rgbValue = objRef.getValue(valueField);
            }
          }
          if (rgbValue == null && getRGBMethod != null) {
            rgbValue = evaluationContext.getDebugProcess().invokeMethod(evaluationContext, objRef, getRGBMethod, Collections.emptyList());
          }
          if (rgbValue instanceof IntegerValue) {
            @SuppressWarnings("UseJBColor")
            Color color = new Color(((IntegerValue)rgbValue).value(), true);
            return JBUI.scale(new ColorIcon(16, 12, color, true));
          }
        }
      }
      catch (Exception e) {
        throw new EvaluateException(e.getMessage(), e);
      }
    }
    return null;
  }
}
