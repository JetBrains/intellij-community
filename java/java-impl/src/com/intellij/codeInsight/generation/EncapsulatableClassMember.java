// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface EncapsulatableClassMember extends ClassMember {

  /**
   * @return PsiElement or TemplateGenerationInfo
   * @deprecated please, use {@link EncapsulatableClassMember#generateGetter(GetterSetterGenerationOptions)}
   */
  @Deprecated
  @Nullable
  GenerationInfo generateGetter() throws IncorrectOperationException;

  /**
   * @return PsiElement or TemplateGenerationInfo
   * @deprecated please, use {@link EncapsulatableClassMember#generateSetter(GetterSetterGenerationOptions)}
   */
  @Deprecated
  @Nullable
  GenerationInfo generateSetter() throws IncorrectOperationException;

  /**
   * @return PsiElement or TemplateGenerationInfo
   */
  default @Nullable GenerationInfo generateGetter(@NotNull GetterSetterGenerationOptions options) throws IncorrectOperationException {
    return generateGetter();
  }

  /**
   * @return PsiElement or TemplateGenerationInfo
   */
  default @Nullable GenerationInfo generateSetter(@NotNull GetterSetterGenerationOptions options) throws IncorrectOperationException {
    return generateSetter();
  }

  /**
   * @return true if the member is definitely read-only (no setter could be generated); false if it's not known.
   */
  default boolean isReadOnlyMember() {
    return false;
  }

}
