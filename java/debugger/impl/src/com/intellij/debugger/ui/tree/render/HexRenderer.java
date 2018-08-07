// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HexRenderer extends NodeRendererImpl {
  public static final @NonNls String UNIQUE_ID = "HexRenderer";
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.HexRenderer");

  public HexRenderer() {
    super(DEFAULT_NAME, false);
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public @NonNls String getName() {
    return "Hex";
  }

  public void setName(String name) {
    // prohibit change
  }

  public HexRenderer clone() {
    return (HexRenderer) super.clone();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String calcLabel(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) {
    Value value = valueDescriptor.getValue();
    StringBuilder buf = new StringBuilder();

    if (value == null) {
      return "null";
    }
    else if (value instanceof CharValue) {
      PrimitiveRenderer.appendCharValue((CharValue)value, buf);
      buf.append(' ');
      appendHexValue((PrimitiveValue)value, buf);
      return buf.toString();
    }
    else {
      appendHexValue((PrimitiveValue)value, buf);
      return buf.toString();
    }
  }

  static void appendHexValue(@NotNull PrimitiveValue value, StringBuilder buf) {
    if (value instanceof CharValue) {
      long longValue = value.longValue();
      buf.append("0x").append(Long.toHexString(longValue).toUpperCase());
    }
    else if (value instanceof ByteValue) {
      String strValue = Integer.toHexString(value.byteValue()).toUpperCase();
      if (strValue.length() > 2) {
        strValue = strValue.substring(strValue.length() - 2);
      }
      buf.append("0x").append(strValue);
    }
    else if (value instanceof ShortValue) {
      String strValue = Integer.toHexString(value.shortValue()).toUpperCase();
      if (strValue.length() > 4) {
        strValue = strValue.substring(strValue.length() - 4);
      }
      buf.append("0x").append(strValue);
    }
    else if (value instanceof IntegerValue) {
      buf.append("0x").append(Integer.toHexString(value.intValue()).toUpperCase());
    }
    else if (value instanceof LongValue) {
      buf.append("0x").append(Long.toHexString(value.longValue()).toUpperCase());
    }
    else {
      LOG.assertTrue(false);
    }
  }

  //returns whether this renderer is apllicable to this type or it's supertypes
  public boolean isApplicable(Type t) {
    if(t == null) {
      return false;
    }
    return t instanceof CharType ||
           t instanceof ByteType ||
           t instanceof ShortType ||
           t instanceof IntegerType ||
           t instanceof LongType;
  }
}
