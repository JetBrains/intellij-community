// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.debugger.ui.tree.render.Renderer;
import com.intellij.debugger.ui.tree.render.ToStringRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.impl.evaluate.XValueCompactPresentation;
import com.intellij.xdebugger.impl.ui.tree.XValueExtendedPresentation;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class JavaValuePresentation extends XValueExtendedPresentation implements XValueCompactPresentation {
  protected final ValueDescriptorImpl myValueDescriptor;

  public JavaValuePresentation(ValueDescriptorImpl valueDescriptor) {
    myValueDescriptor = valueDescriptor;
  }

  @Nullable
  @Override
  public String getType() {
    return myValueDescriptor.getIdLabel();
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer) {
    renderValue(renderer, null);
  }

  @Override
  public void renderValue(@NotNull XValueTextRenderer renderer, @Nullable XValueNodeImpl node) {
    boolean compact = node != null;
    String valueText = myValueDescriptor.getValueText();
    EvaluateException exception = myValueDescriptor.getEvaluateException();
    if (exception != null) {
      String errorMessage = exception.getMessage();
      if (valueText.endsWith(errorMessage)) {
        renderer.renderValue(valueText.substring(0, valueText.length() - errorMessage.length()));
      }
      renderer.renderError(errorMessage);
    }
    else {
      if (compact && node.getValueContainer() instanceof JavaValue) {
        final JavaValue container = (JavaValue)node.getValueContainer();

        if (container.getDescriptor().isArray()) {
          final ArrayReference value = (ArrayReference)container.getDescriptor().getValue();
          final ArrayType type = (ArrayType)container.getDescriptor().getType();
          if (type != null) {
            final String typeName = type.componentTypeName();
            if (TypeConversionUtil.isPrimitive(typeName) || CommonClassNames.JAVA_LANG_STRING.equals(typeName)) {
              int size = value.length();
              int max = Math.min(size, CommonClassNames.JAVA_LANG_STRING.equals(typeName) ? 5 : 10);
              //TODO [eu]: this is a quick fix for IDEA-136606, need to move this away from EDT!!!
              final List<Value> values = value.getValues(0, max);
              int i = 0;
              final List<String> vals = new ArrayList<>(max);
              while (i < values.size()) {
                vals.add(StringUtil.first(values.get(i).toString(), 15, true));
                i++;
              }
              String more = "";
              if (vals.size() < size) {
                more = ", + " + (size - vals.size()) + " more";
              }

              renderer.renderValue("{" + StringUtil.join(vals, ", ") + more + "}");
              return;
            }
          }
        }
      }

      if (myValueDescriptor.isString()) {
        renderer.renderStringValue(valueText, "\"", XValueNode.MAX_VALUE_LENGTH);
        return;
      }

      String value = truncateToMaxLength(valueText);
      Renderer lastRenderer = myValueDescriptor.getLastLabelRenderer();
      if (lastRenderer instanceof ToStringRenderer) {
        if (!((ToStringRenderer)lastRenderer).isShowValue(myValueDescriptor, myValueDescriptor.getStoredEvaluationContext())) {
          return; // to avoid empty line for not calculated toStrings
        }
        value = StringUtil.wrapWithDoubleQuote(value);
      }
      renderer.renderValue(value);
    }
  }

  @NotNull
  @Override
  public String getSeparator() {
    boolean emptyAfterSeparator = !myValueDescriptor.isShowIdLabel() && isValueEmpty();
    String declaredType = myValueDescriptor.getDeclaredTypeLabel();
    if (!StringUtil.isEmpty(declaredType)) {
      return emptyAfterSeparator ? declaredType : declaredType + " " + DEFAULT_SEPARATOR;
    }
    return emptyAfterSeparator ? "" : DEFAULT_SEPARATOR;
  }

  @Override
  public boolean isModified() {
    return myValueDescriptor.isDirty();
  }

  private boolean isValueEmpty() {
    final MyEmptyContainerChecker checker = new MyEmptyContainerChecker();
    renderValue(new XValueTextRendererImpl(checker));
    return checker.isEmpty;
  }

  private static class MyEmptyContainerChecker implements ColoredTextContainer {
    boolean isEmpty = true;

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes) {
      if (!fragment.isEmpty()) isEmpty = false;
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
      append(fragment, attributes);
    }

    @Override
    public void setIcon(@Nullable Icon icon) {
    }

    @Override
    public void setToolTipText(@Nullable String text) {
    }
  }

  private static String truncateToMaxLength(@NotNull String value) {
    return value.substring(0, Math.min(value.length(), XValueNode.MAX_VALUE_LENGTH));
  }
}
