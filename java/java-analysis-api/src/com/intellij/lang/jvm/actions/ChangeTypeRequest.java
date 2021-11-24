// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface ChangeTypeRequest extends ActionRequest {
  /**
   * @return null if type name should not be changed, fully qualified name otherwise 
   */
  @Nullable
  String getQualifiedName();
  
  default List<AnnotationRequest> getAnnotations() {
    return Collections.emptyList();
  }
}
