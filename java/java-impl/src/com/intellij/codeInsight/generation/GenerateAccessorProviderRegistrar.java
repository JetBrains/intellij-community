// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public final class GenerateAccessorProviderRegistrar {

  public static final ExtensionPointName<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> EP_NAME = ExtensionPointName.create("com.intellij.generateAccessorProvider");

  static synchronized @Unmodifiable List<EncapsulatableClassMember> getEncapsulatableClassMembers(final PsiClass psiClass) {
    return ContainerUtil.concat(EP_NAME.getExtensionList(), s -> s.fun(psiClass));
  }
}
