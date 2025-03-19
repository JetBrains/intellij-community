// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface AnnotationRequest extends ActionRequest {

  @NotNull
  String getQualifiedName();

  default @NotNull List<AnnotationAttributeRequest> getAttributes() {
    return Collections.emptyList();
  }

}
