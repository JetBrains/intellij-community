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

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.util.ui.ColorIcon;
import com.sun.jdi.*;

import javax.swing.*;
import java.awt.*;

/**
* Created by Egor on 04.10.2014.
*/
class ColorObjectRenderer extends ToStringBasedRenderer {
  public ColorObjectRenderer(final NodeRendererSettings rendererSettings) {
    super(rendererSettings, "Color", null, null);
    setClassName("java.awt.Color");
    setEnabled(true);
  }

  public Icon calcValueIcon(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener) throws EvaluateException {
    final Value value = descriptor.getValue();
    if (value instanceof ObjectReference) {
      try {
        final ObjectReference objRef = (ObjectReference)value;
        final ReferenceType refType = objRef.referenceType();
        final Field valueField = refType.fieldByName("value");
        if (valueField != null) {
          final Value rgbValue = objRef.getValue(valueField);
          if (rgbValue instanceof IntegerValue) {
            @SuppressWarnings("UseJBColor")
            final Color color = new Color(((IntegerValue)rgbValue).value(), true);
            return new ColorIcon(16, 12, color, true);
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
