/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import org.jetbrains.annotations.Nullable;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public interface EncapsulatableClassMember extends ClassMember{
  /**
   * @return PsiElement or TemplateGenerationInfo
   */
  @Nullable
  GenerationInfo generateGetter() throws IncorrectOperationException;

  /**
   * @return PsiElement or TemplateGenerationInfo
   */
  @Nullable
  GenerationInfo generateSetter() throws IncorrectOperationException;
}
