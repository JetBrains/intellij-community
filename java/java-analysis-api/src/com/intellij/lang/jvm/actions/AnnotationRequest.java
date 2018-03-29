// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface AnnotationRequest extends ActionRequest {

  @NotNull
  String getQualifiedName();

  @NotNull
  default List<AnnotationAttributeRequest> getAttributes() {
    return Collections.emptyList();
  }

}
