// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface ChangeParametersRequest extends ActionRequest {

  List<ExpectedParameter> getExpectedParameters();

  /**
   * Designed to be used inside {@link JvmElementActionsFactory} implementations.
   * From the API calling side use {@link MethodRequestsKt#updateMethodParametersRequest(Supplier, Function)}
   */
  class ExistingParameterWrapper implements ExpectedParameter {

    private final JvmParameter myExistingParameter;

    public ExistingParameterWrapper(@NotNull JvmParameter existingParameter) {
      myExistingParameter = existingParameter;
    }

    @Override
    public @NotNull List<ExpectedType> getExpectedTypes() {
      return Collections.singletonList(new SimpleExpectedType(myExistingParameter.getType(), ExpectedType.Kind.EXACT));
    }

    @Override
    public @NotNull Collection<String> getSemanticNames() {
      return Collections.singletonList(myExistingParameter.getName());
    }

    public @NotNull JvmParameter getExistingParameter() {
      return myExistingParameter;
    }

    @Override
    public @NotNull @Unmodifiable Collection<AnnotationRequest> getExpectedAnnotations() {
      return ContainerUtil.mapNotNull(myExistingParameter.getAnnotations(), AnnotationRequestsKt::annotationRequest);
    }
  }
}
