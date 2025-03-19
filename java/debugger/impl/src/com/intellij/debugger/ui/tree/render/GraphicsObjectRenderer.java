// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.sun.jdi.*;

import javax.swing.*;

public final class GraphicsObjectRenderer extends CompoundRendererProvider {
  @Override
  protected String getName() {
    return "Graphics";
  }

  @Override
  protected String getClassName() {
    return "sun.java2d.SunGraphics2D";
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return (evaluationContext, valueDescriptor) -> {
      try {
        ObjectReference value = (ObjectReference)valueDescriptor.getValue();
        Field surfaceField = DebuggerUtils.findField(((ClassType)value.type()), "surfaceData");
        if (surfaceField == null) return null;
        ObjectReference surfaceDataValue = (ObjectReference)value.getValue(surfaceField);
        if (surfaceDataValue == null) return null;

        Field imgField = DebuggerUtils.findField(((ReferenceType)surfaceDataValue.type()), "bufImg"); // BufImgSurfaceData
        if (imgField == null) {
          imgField = DebuggerUtils.findField(((ReferenceType)surfaceDataValue.type()), "offscreenImage"); // CGLSurfaceData
        }
        if (imgField == null) return null;

        final Value bufImgValue = surfaceDataValue.getValue(imgField);
        Type type = bufImgValue.type();
        if (!(type instanceof ReferenceType) || !DebuggerUtils.instanceOf(type, "java.awt.Image")) {
          return null;
        }
        return new ImageObjectRenderer.IconPopupEvaluator(JavaDebuggerBundle.message("message.node.show.image"), evaluationContext) {
          @Override
          protected Icon getData() {
            return ImageObjectRenderer.getIcon(getEvaluationContext(), bufImgValue, "imageToBytes");
          }
        };
      }
      catch (Exception ignored) {
      }
      return null;
    };
  }
}
