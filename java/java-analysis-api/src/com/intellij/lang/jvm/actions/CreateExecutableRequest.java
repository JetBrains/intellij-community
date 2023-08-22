// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface CreateExecutableRequest extends ActionRequest {

  @NotNull
  Collection<JvmModifier> getModifiers();

  @NotNull
  Collection<AnnotationRequest> getAnnotations();

  @NotNull
  JvmSubstitutor getTargetSubstitutor();

  @NotNull
  List<ExpectedParameter> getExpectedParameters();
}
