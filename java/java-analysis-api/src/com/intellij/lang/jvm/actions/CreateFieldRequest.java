// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.JvmValue;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface CreateFieldRequest extends ActionRequest {
  @NotNull
  String getFieldName();

  @NotNull
  List<ExpectedType> getFieldType();

  /**
   * Given:
   * - target class: {@code A<T>}
   * - expected field type: {@code String}
   * - usage: {@code new A<String>.foo}
   * <p>
   * To make newly created field {@code foo} have type {@code T} the substitutor is needed to provide mapping T -> String.
   *
   * @return call-site substitutor for the target
   */
  @NotNull
  JvmSubstitutor getTargetSubstitutor();

  /**
   * The field initializer. Currently, only value initializers are supported.
   */
  @Nullable
  JvmValue getInitializer();

  @NotNull
  Collection<AnnotationRequest> getAnnotations();

  /**
   * Implementation are free to render any modifiers as long as they don't contradict with requested ones.
   * Example: if constant field is requested then it will be rendered
   * with {@code static final} modifiers even if they are not present in this collection.
   *
   * @return modifiers that should be present when requested field is compiled
   */
  @NotNull
  Collection<JvmModifier> getModifiers();

  /**
   * Constant fields may be used in annotations and in other constant expressions.
   *
   * @return whether the field must be a compile-time constant
   */
  boolean isConstant();

  /**
   * Only for Kotlin
   * Determines whether an empty initializer should be created for the field.
   *
   * @return true if an empty initializer should be created, false otherwise.
   */
  default boolean isCreateEmptyInitializer() { return true; }

  /**
   * @return should start live template after a new field was created.
   */
  default boolean isStartTemplate() {
    return true;
  }
}
