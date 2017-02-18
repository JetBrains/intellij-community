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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiClass;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class GenerateAccessorProviderRegistrar {

  public final static ExtensionPointName<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> EP_NAME = ExtensionPointName.create("com.intellij.generateAccessorProvider");

  private static final List<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> ourProviders = new ArrayList<>();

  static {
    ourProviders.addAll(Arrays.asList(Extensions.getExtensions(EP_NAME)));
  }

  /** @see #EP_NAME */
  @Deprecated
  public synchronized static void registerProvider(NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> function) {
    ourProviders.add(function);
  }

  protected synchronized static List<EncapsulatableClassMember> getEncapsulatableClassMembers(final PsiClass psiClass) {
    return ContainerUtil.concat(ourProviders, s -> s.fun(psiClass));
  }
}
