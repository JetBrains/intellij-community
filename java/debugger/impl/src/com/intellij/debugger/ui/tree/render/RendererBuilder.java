// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.Type;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class RendererBuilder {
  private static final Logger LOG = Logger.getInstance(RendererBuilder.class);

  private final String myName;
  private String myClassName;
  private ValueLabelRenderer myValueLabelRenderer;
  private ValueIconRenderer myIconRenderer;
  private ChildrenRenderer myChildrenRenderer;
  private FullValueEvaluatorProvider myFullValueEvaluatorProvider;
  private Function<Type, CompletableFuture<Boolean>> myIsApplicableChecker;
  private boolean myEnabled;

  public RendererBuilder(String name) {
    myName = name;
  }

  public RendererBuilder labelRenderer(ValueLabelRenderer labelRenderer) {
    myValueLabelRenderer = labelRenderer;
    return this;
  }

  public RendererBuilder childrenRenderer(ChildrenRenderer childrenRenderer) {
    myChildrenRenderer = childrenRenderer;
    return this;
  }

  public RendererBuilder iconRenderer(ValueIconRenderer iconRenderer) {
    myIconRenderer = iconRenderer;
    return this;
  }

  public RendererBuilder fullValueEvaluator(FullValueEvaluatorProvider fullValueEvaluator) {
    myFullValueEvaluatorProvider = fullValueEvaluator;
    return this;
  }

  public RendererBuilder isApplicable(Function<Type, CompletableFuture<Boolean>> isApplicableChecker) {
    if (myClassName != null) {
      LOG.warn("Using isApplicable together with isApplicableForInheritors, will ignore className");
    }
    myIsApplicableChecker = isApplicableChecker;
    return this;
  }

  public RendererBuilder isApplicableForInheritors(String className) {
    if (myIsApplicableChecker != null) {
      LOG.warn("Using isApplicable together with isApplicableForInheritors, will ignore className");
    }
    myClassName = className;
    return this;
  }

  public RendererBuilder enabled(boolean enabled) {
    myEnabled = enabled;
    return this;
  }

  public NodeRenderer build() {
    CompoundReferenceRenderer res = new CompoundReferenceRenderer(myName, myValueLabelRenderer, myChildrenRenderer);
    res.setIconRenderer(myIconRenderer);
    res.setFullValueEvaluator(myFullValueEvaluatorProvider);
    if (myIsApplicableChecker != null) {
      res.setIsApplicableChecker(myIsApplicableChecker);
    }
    else if (!StringUtil.isEmpty(myClassName)) {
      res.setClassName(myClassName);
      res.setIsApplicableChecker(type -> DebuggerUtilsAsync.instanceOf(type, res.getClassName()));
    }
    else {
      LOG.error("IsApplicable is not defined for " + myName);
    }
    res.setEnabled(myEnabled);
    return res;
  }
}
