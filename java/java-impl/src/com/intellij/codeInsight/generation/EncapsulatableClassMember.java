/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation;

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public interface EncapsulatableClassMember extends ClassMember {


  /**
   * @return PsiElement or TemplateGenerationInfo
   * @deprecated please, use {@link EncapsulatableClassMember#generateGetter(EnumSet)}
   */
  @Deprecated
  @Nullable
  GenerationInfo generateGetter() throws IncorrectOperationException;

  /**
   * @return PsiElement or TemplateGenerationInfo
   * @deprecated please, use {@link EncapsulatableClassMember#generateSetter(EnumSet)}
   */
  @Deprecated
  @Nullable
  GenerationInfo generateSetter() throws IncorrectOperationException;

  /**
   * @return PsiElement or TemplateGenerationInfo
   */
  @Nullable
  default GenerationInfo generateGetter(@NotNull EnumSet<Option> options) throws IncorrectOperationException {
    return generateGetter();
  }

  /**
   * @return PsiElement or TemplateGenerationInfo
   */
  @Nullable
  default GenerationInfo generateSetter(@NotNull EnumSet<Option> options) throws IncorrectOperationException {
    return generateSetter();
  }

  /**
   * @return true if the member is definitely read-only (no setter could be generated); false if it's not known.
   */
  default boolean isReadOnlyMember() {
    return false;
  }

  enum Option {
    COPY_ALL_ANNOTATIONS
  }
}
