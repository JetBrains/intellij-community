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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class ToStringRenderer extends NodeRendererImpl implements OnDemandRenderer {
  public static final @NonNls String UNIQUE_ID = "ToStringRenderer";

  private boolean USE_CLASS_FILTERS = false;
  private boolean ON_DEMAND;
  private ClassFilter[] myClassFilters = ClassFilter.EMPTY_ARRAY;

  public ToStringRenderer() {
    super("unnamed", true);
  }

  @Override
  public String getUniqueId() {
    return UNIQUE_ID;
  }

  @Override
  public String getName() {
    return "toString";
  }

  @Override
  public void setName(String name) {
    // prohibit change
  }

  @Override
  public ToStringRenderer clone() {
    final ToStringRenderer cloned = (ToStringRenderer)super.clone();
    final ClassFilter[] classFilters = (myClassFilters.length > 0)? new ClassFilter[myClassFilters.length] : ClassFilter.EMPTY_ARRAY;
    for (int idx = 0; idx < classFilters.length; idx++) {
      classFilters[idx] = myClassFilters[idx].clone();
    }
    cloned.myClassFilters = classFilters;
    return cloned;
  }

  @Override
  public String calcLabel(final ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, final DescriptorLabelListener labelListener)
    throws EvaluateException {

    if (!isShowValue(valueDescriptor, evaluationContext)) {
      return "";
    }

    final Value value = valueDescriptor.getValue();
    BatchEvaluator.getBatchEvaluator(evaluationContext.getDebugProcess()).invoke(new ToStringCommand(evaluationContext, value) {
      @Override
      public void evaluationResult(String message) {
        valueDescriptor.setValueLabel(
          StringUtil.notNullize(message)
        );
        labelListener.labelChanged();
      }

      @Override
      public void evaluationError(String message) {
        final String msg = value != null? message + " " + DebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", value.type().name()) : message;
        valueDescriptor.setValueLabelFailed(new EvaluateException(msg, null));
        labelListener.labelChanged();
      }
    });
    return XDebuggerUIConstants.COLLECTING_DATA_MESSAGE;
  }

  @NotNull
  @Override
  public String getLinkText() {
    return DebuggerBundle.message("message.node.toString");
  }

  public boolean isUseClassFilters() {
    return USE_CLASS_FILTERS;
  }

  public void setUseClassFilters(boolean value) {
    USE_CLASS_FILTERS = value;
  }

  @Override
  public boolean isOnDemand(EvaluationContext evaluationContext, ValueDescriptor valueDescriptor) {
    if (ON_DEMAND || (USE_CLASS_FILTERS && !isFiltered(valueDescriptor.getType()))) {
      return true;
    }
    return OnDemandRenderer.super.isOnDemand(evaluationContext, valueDescriptor);
  }

  @Override
  public boolean isApplicable(Type type) {
    if (!(type instanceof ReferenceType)) {
      return false;
    }

    if (JAVA_LANG_STRING.equals(type.name())) {
      return false; // do not render 'String' objects for performance reasons
    }

    return overridesToString(type);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static boolean overridesToString(Type type) {
    if (type instanceof ClassType) {
      Method toStringMethod = ((ClassType)type).concreteMethodByName("toString", "()Ljava/lang/String;");
      return toStringMethod != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(toStringMethod.declaringType().name());
    }
    return false;
  }

  @Override
  public void buildChildren(Value value, ChildrenBuilder builder, EvaluationContext evaluationContext) {
    DebugProcessImpl.getDefaultRenderer(value).buildChildren(value, builder, evaluationContext);
  }

  @Override
  public PsiElement getChildValueExpression(DebuggerTreeNode node, DebuggerContext context) throws EvaluateException {
    return DebugProcessImpl.getDefaultRenderer(((ValueDescriptor)node.getParent().getDescriptor()).getType())
      .getChildValueExpression(node, context);
  }

  @Override
  public boolean isExpandable(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return DebugProcessImpl.getDefaultRenderer(value).isExpandable(value, evaluationContext, parentDescriptor);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void readExternal(Element element) {
    super.readExternal(element);

    ON_DEMAND = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "ON_DEMAND"));
    USE_CLASS_FILTERS = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_CLASS_FILTERS"));
    myClassFilters = DebuggerUtilsEx.readFilters(element.getChildren("filter"));
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void writeExternal(Element element) {
    super.writeExternal(element);

    if (ON_DEMAND) {
      JDOMExternalizerUtil.writeField(element, "ON_DEMAND", "true");
    }
    if (USE_CLASS_FILTERS) {
      JDOMExternalizerUtil.writeField(element, "USE_CLASS_FILTERS", "true");
    }
    DebuggerUtilsEx.writeFilters(element, "filter", myClassFilters);
  }

  public ClassFilter[] getClassFilters() {
    return myClassFilters;
  }

  public void setClassFilters(ClassFilter[] classFilters) {
    myClassFilters = classFilters != null ? classFilters : ClassFilter.EMPTY_ARRAY;
  }

  private boolean isFiltered(Type t) {
    if (t instanceof ReferenceType) {
      for (ClassFilter classFilter : myClassFilters) {
        if (classFilter.isEnabled() && DebuggerUtils.instanceOf(t, classFilter.getPattern())) {
          return true;
        }
      }
    }
    return DebuggerUtilsEx.isFiltered(t.name(), myClassFilters);
  }

  public boolean isOnDemand() {
    return ON_DEMAND;
  }

  public void setOnDemand(boolean value) {
    ON_DEMAND = value;
  }

  @Override
  public boolean hasOverhead() {
    return true;
  }
}
