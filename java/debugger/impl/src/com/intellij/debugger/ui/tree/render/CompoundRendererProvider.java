// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.FullValueEvaluatorProvider;
import com.intellij.debugger.impl.DebuggerUtilsAsync;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.Type;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Allows to construct a renderer with the provided capabilities
 */
public abstract class CompoundRendererProvider {
  public static final ExtensionPointName<CompoundRendererProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.debugger.compoundRendererProvider");

  protected abstract String getName();

  protected String getClassName() {
    return null;
  }

  protected ValueLabelRenderer getValueLabelRenderer() {
    return null;
  }

  protected ValueIconRenderer getIconRenderer() {
    return null;
  }

  protected ChildrenRenderer getChildrenRenderer() {
    return null;
  }

  protected FullValueEvaluatorProvider getFullValueEvaluatorProvider() {
    return null;
  }

  protected Function<Type, CompletableFuture<Boolean>> getIsApplicableChecker() {
    return null;
  }

  protected boolean isEnabled() {
    return false;
  }

  @NotNull
  public final NodeRenderer createRenderer() {
    CompoundReferenceRenderer res = new CompoundReferenceRenderer(getName(), getValueLabelRenderer(), getChildrenRenderer());
    res.setIconRenderer(getIconRenderer());
    res.setFullValueEvaluator(getFullValueEvaluatorProvider());
    String className = getClassName();
    if (!StringUtil.isEmpty(className)) {
      res.setClassName(className);
      res.setIsApplicableChecker(type -> DebuggerUtilsAsync.instanceOf(type, res.getClassName()));
    }
    Function<Type, CompletableFuture<Boolean>> isApplicableChecker = getIsApplicableChecker();
    if (isApplicableChecker != null) {
      res.setIsApplicableChecker(isApplicableChecker);
    }
    res.setEnabled(isEnabled());
    return res;
  }
}
