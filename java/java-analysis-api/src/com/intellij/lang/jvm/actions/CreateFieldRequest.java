// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions;

import com.intellij.lang.jvm.JvmModifier;
import com.intellij.lang.jvm.types.JvmSubstitutor;
import com.intellij.psi.PsiElement;
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


  @Nullable
  PsiElement getInitializer();

  /**
   * Implementation are free to render any modifiers as long as they don't contradict with requested ones.
   * Example: if constant field is requested then it will be rendered
   * with {@code static final} modifiers even if they are not present in this collection.
   *
   * @return modifiers that should be present when requested field is compiled
   */
  @NotNull
  Collection<JvmModifier> getModifiers();

  boolean isConstant();
}
