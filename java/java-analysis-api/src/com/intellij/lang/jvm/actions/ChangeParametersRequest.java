// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

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

    @NotNull
    @Override
    public List<ExpectedType> getExpectedTypes() {
      return Collections.singletonList(new SimpleExpectedType(myExistingParameter.getType(), ExpectedType.Kind.EXACT));
    }

    @NotNull
    @Override
    public Collection<String> getSemanticNames() {
      return Collections.singletonList(myExistingParameter.getName());
    }

    @NotNull
    public JvmParameter getExistingParameter() {
      return myExistingParameter;
    }

    @NotNull
    @Override
    public Collection<AnnotationRequest> getExpectedAnnotations() {
      return ContainerUtil.mapNotNull(myExistingParameter.getAnnotations(), AnnotationRequestsKt::annotationRequest);
    }
  }
}
