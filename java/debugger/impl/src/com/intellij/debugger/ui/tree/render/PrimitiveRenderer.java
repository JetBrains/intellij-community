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
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiExpression;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * User: lex
 * Date: Sep 18, 2003
 * Time: 3:07:27 PM
 */
public class PrimitiveRenderer extends NodeRendererImpl {
  public static final @NonNls String UNIQUE_ID = "PrimitiveRenderer";
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.PrimitiveRenderer");

  public boolean SHOW_HEX_VALUE = false;

  public PrimitiveRenderer() {
    super("Primitive");
  }

  public String getUniqueId() {
    return UNIQUE_ID;
  }

  public void setName(String text) {
    // prohibit name change
  }

  public final boolean isEnabled() {
    return true;
  }

  public void setEnabled(boolean enabled) {
    // prohibit change
  }

  public boolean isApplicable(Type type) {
    return type == null || type instanceof PrimitiveType || type instanceof VoidType;
  }

  public String calcLabel(ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, DescriptorLabelListener labelListener) {
    Value value = valueDescriptor.getValue();
    if (value == null) {
      //noinspection HardCodedStringLiteral
      return "null";
    }
    else if (value instanceof PrimitiveValue) {
      if (value instanceof CharValue) {
        StringBuilder buf = new StringBuilder();
        appendCharValue((CharValue)value, buf);
        if (SHOW_HEX_VALUE) {
          appendHexValue((CharValue)value, buf);
        } else {
          buf.append(' ').append(((PrimitiveValue)value).longValue());
        }
        return buf.toString();
      }
      else {
        if (SHOW_HEX_VALUE) {
          StringBuilder buf = new StringBuilder();
          buf.append(value.toString());
          appendHexValue((PrimitiveValue)value, buf);
          return buf.toString();
        }
        else {
          return value.toString();
        }
      }
    }
    else {
      return DebuggerBundle.message("label.undefined");
    }
  }

  static void appendCharValue(CharValue value, StringBuilder buf) {
    buf.append('\'');
    String s = value.toString();
    StringUtil.escapeStringCharacters(s.length(), s, "\'", buf);
    buf.append('\'');
  }

  private static void appendHexValue(PrimitiveValue value, StringBuilder buf) {
    if (NodeRendererSettings.getInstance().getHexRenderer().isApplicable(value.type())) {
      buf.append(" (");
      HexRenderer.appendHexValue(value, buf);
      buf.append(')');
    }
  }

  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
  }

  public PsiExpression getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) {
    LOG.assertTrue(false);
    return null;
  }

  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return false;
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
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
