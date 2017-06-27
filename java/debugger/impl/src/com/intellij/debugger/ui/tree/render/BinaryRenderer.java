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
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;

/**
 * @author egor
 */
public class BinaryRenderer extends NodeRendererImpl {
  private static final Logger LOG = Logger.getInstance(BinaryRenderer.class);

  @Override
  public String calcLabel(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, DescriptorLabelListener listener)
    throws EvaluateException {
    Value value = valueDescriptor.getValue();

    if (value == null) {
      return "null";
    }

    StringBuilder buf = new StringBuilder("0b");
    int prefixLength = buf.length();
    String valueStr = "";
    if (value instanceof ByteValue) {
      valueStr = Integer.toBinaryString(0xff & ((ByteValue)value).byteValue());
    }
    else if (value instanceof ShortValue) {
      valueStr = Integer.toBinaryString(0xffff & ((ShortValue)value).shortValue());
    }
    else if (value instanceof IntegerValue) {
      valueStr = Integer.toBinaryString(((PrimitiveValue)value).intValue());
    }
    else if (value instanceof LongValue) {
      valueStr = Long.toBinaryString(((LongValue)value).longValue());
    }
    else {
      LOG.error("Unsupported value " + value);
    }

    // add leading zeros
    int remainder = valueStr.length() % 8;
    if (remainder != 0) {
      for (int i = 0; i < 8 - remainder; i++) {
        buf.append('0');
      }
    }

    buf.append(valueStr);

    // group by 8
    for (int i = buf.length() - 8; i > prefixLength; i -= 8) {
      buf.insert(i, '_');
    }
    return buf.toString();
  }

  @Override
  public String getName() {
    return "Binary";
  }

  @Override
  public String getUniqueId() {
    return "BinaryRenderer";
  }

  @Override
  public boolean isApplicable(Type t) {
    if (t == null) {
      return false;
    }
    return t instanceof ByteType ||
           t instanceof ShortType ||
           t instanceof IntegerType ||
           t instanceof LongType;
  }
}
