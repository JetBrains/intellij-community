// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.DebuggerContext;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.settings.DebuggerSettingsUtils;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.sun.jdi.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class ToStringRenderer extends NodeRendererImpl implements OnDemandRenderer {
  public static final @NonNls String UNIQUE_ID = "ToStringRenderer";

  private boolean USE_CLASS_FILTERS = false;
  private boolean ON_DEMAND;
  private ClassFilter[] myClassFilters = ClassFilter.EMPTY_ARRAY;

  public ToStringRenderer() {
    super(DEFAULT_NAME, true);
    setIsApplicableChecker(type -> {
      // do not render 'String' objects for performance reasons
      if (!(type instanceof ReferenceType) || JAVA_LANG_STRING.equals(type.name())) {
        return CompletableFuture.completedFuture(false);
      }
      return overridesToStringAsync(type);
    });
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
    cloned.myClassFilters = ClassFilter.deepCopyOf(myClassFilters);
    return cloned;
  }

  @Override
  public String calcLabel(final ValueDescriptor valueDescriptor, EvaluationContext evaluationContext, final DescriptorLabelListener labelListener)
    throws EvaluateException {

    if (!isShowValue(valueDescriptor, evaluationContext)) {
      return "";
    }

    Value value = valueDescriptor.getValue();
    if (value instanceof ObjectReference) {
      DebuggerUtils.ensureNotInsideObjectConstructor((ObjectReference)value, evaluationContext);
    }
    BatchEvaluator.getBatchEvaluator(evaluationContext).invoke(new ToStringCommand(evaluationContext, value) {
      @Override
      public void evaluationResult(String message) {
        valueDescriptor.setValueLabel(
          StringUtil.notNullize(message)
        );
        labelListener.labelChanged();
      }

      @Override
      public void evaluationError(String message) {
        final String msg = value != null ? message + " " + JavaDebuggerBundle
          .message("evaluation.error.cannot.evaluate.tostring", value.type().name()) : message;
        valueDescriptor.setValueLabelFailed(new EvaluateException(msg, null));
        labelListener.labelChanged();
      }
    });
    return XDebuggerUIConstants.getCollectingDataMessage();
  }

  @Override
  public @NotNull String getLinkText() {
    return JavaDebuggerBundle.message("message.node.toString");
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

  private static boolean overridesToString(Type type) {
    if (type instanceof ClassType) {
      Method toStringMethod = DebuggerUtils.findMethod((ReferenceType)type, "toString", "()Ljava/lang/String;");
      return toStringMethod != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(toStringMethod.declaringType().name());
    }
    return false;
  }

  private static CompletableFuture<Boolean> overridesToStringAsync(Type type) {
    if (!DebuggerUtilsAsync.isAsyncEnabled()) {
      return CompletableFuture.completedFuture(overridesToString(type));
    }
    if (type instanceof ClassType) {
      return DebuggerUtilsAsync.findAnyBaseType(type, t -> {
        if (t instanceof ReferenceType) {
          return DebuggerUtilsAsync.methods((ReferenceType)t)
            .thenApply(methods -> {
              return ContainerUtil.exists(methods,
                                          m -> !m.isAbstract() &&
                                               DebuggerUtilsEx.methodMatches(m, "toString", "()Ljava/lang/String;") &&
                                               !CommonClassNames.JAVA_LANG_OBJECT.equals(m.declaringType().name()));
            });
        }
        return CompletableFuture.completedFuture(false);
      }).thenApply(t -> t != null);
    }
    return CompletableFuture.completedFuture(false);
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
  public CompletableFuture<Boolean> isExpandableAsync(Value value, EvaluationContext evaluationContext, NodeDescriptor parentDescriptor) {
    return DebugProcessImpl.getDefaultRenderer(value).isExpandableAsync(value, evaluationContext, parentDescriptor);
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);

    ON_DEMAND = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "ON_DEMAND"));
    USE_CLASS_FILTERS = Boolean.parseBoolean(JDOMExternalizerUtil.readField(element, "USE_CLASS_FILTERS"));
    myClassFilters = DebuggerSettingsUtils.readFilters(element.getChildren("filter"));
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);

    if (ON_DEMAND) {
      JDOMExternalizerUtil.writeField(element, "ON_DEMAND", "true");
    }
    if (USE_CLASS_FILTERS) {
      JDOMExternalizerUtil.writeField(element, "USE_CLASS_FILTERS", "true");
    }
    DebuggerSettingsUtils.writeFilters(element, "filter", myClassFilters);
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
    return DebuggerUtilsEx.isFiltered(t.name(), Arrays.stream(myClassFilters));
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

  /**
   * for kotlin compatibility only
   *
   * @deprecated to be removed in IDEA 2021
   */
  @Deprecated
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
}
