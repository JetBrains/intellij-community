/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiClass;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

/**
 * @author peter
 */
public class GenerateAccessorProviderRegistrar {
  private static final List<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> ourProviders = new ArrayList<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>>();

  public static void registerProvider(NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> function) {
    ourProviders.add(function);
  }

  protected static List<EncapsulatableClassMember> getEncapsulatableClassMembers(final PsiClass psiClass) {
    return ContainerUtil.concat(ourProviders, new Function<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>, Collection<? extends EncapsulatableClassMember>>() {
      public Collection<? extends EncapsulatableClassMember> fun(NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> s) {
        return s.fun(psiClass);
      }
    });
  }

}
