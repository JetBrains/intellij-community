// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

//TODO: probably should be merged into AnnotationRequest
public interface CreateAnnotationRequest extends ActionRequest, AnnotationRequest {

  @NotNull
  String getQualifiedName();

  @NotNull
  List<Pair<String, JvmAnnotationMemberValue>> getAttributes();

  default boolean omitAttributeNameIfPossible(String name) {
    return true;
  }
}
