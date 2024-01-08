// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import org.jetbrains.annotations.NotNull;

public interface IntentionActionDelegate extends ReportingClassSubstitutor {
  @NotNull
  IntentionAction getDelegate();

  static @NotNull IntentionAction unwrap(@NotNull IntentionAction action) {
    return action instanceof IntentionActionDelegate ? unwrap(((IntentionActionDelegate)action).getDelegate()) : action;
  }

  @Override
  @NotNull
  default Class<?> getSubstitutedClass() {
    return ReportingClassSubstitutor.getClassToReport(getDelegate());
  }

  // optimization method: it's not necessary to build extension delegate to know its class
  default @NotNull String getImplementationClassName() {
    return unwrap(getDelegate()).getClass().getName();
  }
}
