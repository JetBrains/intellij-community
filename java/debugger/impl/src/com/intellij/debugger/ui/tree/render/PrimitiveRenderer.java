// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JavaDebuggerMagicConstantUtils;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.ByteValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.LongValue;
import com.sun.jdi.PrimitiveType;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ShortValue;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VoidType;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class PrimitiveRenderer extends NodeRendererImpl {
  public static final @NonNls String UNIQUE_ID = "PrimitiveRenderer";

  public boolean SHOW_HEX_VALUE = false;

  public PrimitiveRenderer() {
    super("Primitive");
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public void setName(String text) {
    // prohibit name change
  }

  @Override
  public final boolean isEnabled() {
    return true;
  }

  @Override
  public void setEnabled(boolean enabled) {
    // prohibit change
  }

  @Override
  public boolean isApplicable(Type type) {
    return type == null || type instanceof PrimitiveType || type instanceof VoidType;
  }

  @Override
  public String calcLabel(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) {
    Value value = valueDescriptor.getValue();
    if (value == null) {
      return "null";
    }
    else if (value instanceof PrimitiveValue) {
      String label;
      if (value instanceof CharValue) {
        StringBuilder buf = new StringBuilder();
        appendCharValue((CharValue)value, buf);
        if (SHOW_HEX_VALUE) {
          appendHexValue((CharValue)value, buf);
        }
        else {
          buf.append(' ').append(((PrimitiveValue)value).longValue());
        }
        label = buf.toString();
      }
      else if (SHOW_HEX_VALUE) {
        StringBuilder buf = new StringBuilder();
        buf.append(value);
        appendHexValue((PrimitiveValue)value, buf);
        label = buf.toString();
      }
      else {
        label = value.toString();
      }
      if (isMagicConstantCandidate(value)
          && valueDescriptor instanceof ValueDescriptorImpl descriptorImpl
          && evaluationContext instanceof EvaluationContextImpl contextImpl) {
        JavaDebuggerMagicConstantUtils.scheduleMagicConstantSuffix(descriptorImpl, contextImpl, label, labelListener);
      }
      return label;
    }
    else {
      return JavaDebuggerBundle.message("label.undefined");
    }
  }

  private static boolean isMagicConstantCandidate(Value value) {
    return value instanceof IntegerValue
           || value instanceof LongValue
           || value instanceof ShortValue
           || value instanceof ByteValue;
  }

  static void appendCharValue(CharValue value, StringBuilder buf) {
    buf.append('\'');
    String s = value.toString();
    StringUtil.escapeStringCharacters(s.length(), s, "'", buf);
    buf.append('\'');
  }

  private static void appendHexValue(PrimitiveValue value, StringBuilder buf) {
    if (NodeRendererSettings.getInstance().getHexRenderer().isApplicable(value.type())) {
      buf.append(" (");
      HexRenderer.appendHexValue(value, buf);
      buf.append(')');
    }
  }

  public boolean isShowHexValue() {
    return SHOW_HEX_VALUE;
  }

  public void setShowHexValue(boolean show) {
    this.SHOW_HEX_VALUE = show;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);

    if (SHOW_HEX_VALUE) {
      JDOMExternalizerUtil.writeField(element, "SHOW_HEX_VALUE", "true");
    }
  }
}
