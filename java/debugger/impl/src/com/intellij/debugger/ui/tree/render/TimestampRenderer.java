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

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.sun.jdi.LongType;
import com.sun.jdi.LongValue;
import com.sun.jdi.Type;
import com.sun.jdi.Value;

import java.sql.Timestamp;

/**
 * @author egor
 */
public class TimestampRenderer extends NodeRendererImpl {
  @Override
  public String calcLabel(ValueDescriptor descriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    Value value = descriptor.getValue();
    if (value == null) {
      return "null";
    }
    else if (value instanceof LongValue) {
      return new Timestamp(((LongValue)value).longValue()).toString();
    }
    return null;
  }

  @Override
  public String getName() {
    return "Timestamp";
  }

  @Override
  public String getUniqueId() {
    return "TimestampRenderer";
  }

  @Override
  public boolean isApplicable(Type t) {
    return t instanceof LongType;
  }
}
