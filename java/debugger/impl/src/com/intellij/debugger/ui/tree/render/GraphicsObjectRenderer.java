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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.sun.jdi.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author egor
 */
public class GraphicsObjectRenderer extends ToStringBasedRenderer implements FullValueEvaluatorProvider {
  public GraphicsObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "Graphics", null, null);
    setClassName("sun.java2d.SunGraphics2D");
    setEnabled(true);
  }

  @Nullable
  @Override
  public XFullValueEvaluator getFullValueEvaluator(final EvaluationContextImpl evaluationContext, final ValueDescriptorImpl valueDescriptor) {
    try {
      ObjectReference value = (ObjectReference)valueDescriptor.getValue();
      Field surfaceField = ((ClassType)value.type()).fieldByName("surfaceData");
      if (surfaceField == null) return null;
      ObjectReference surfaceDataValue = (ObjectReference)value.getValue(surfaceField);
      if (surfaceDataValue == null) return null;

      Field imgField = ((ReferenceType)surfaceDataValue.type()).fieldByName("bufImg"); // BufImgSurfaceData
      if (imgField == null) {
        imgField = ((ReferenceType)surfaceDataValue.type()).fieldByName("offscreenImage"); // CGLSurfaceData
      }
      if (imgField == null) return null;

      final Value bufImgValue = surfaceDataValue.getValue(imgField);
      Type type = bufImgValue.type();
      if (!(type instanceof ReferenceType) || !DebuggerUtils.instanceOf(type, "java.awt.Image")) {
        return null;
      }
      return new ImageObjectRenderer.IconPopupEvaluator(DebuggerBundle.message("message.node.show.image"), evaluationContext) {
        @Override
        protected Icon getData() {
          return ImageObjectRenderer.getIcon(getEvaluationContext(), bufImgValue, "imageToBytes");
        }
      };
    } catch (Exception ignored) {}
    return null;
  }
}
