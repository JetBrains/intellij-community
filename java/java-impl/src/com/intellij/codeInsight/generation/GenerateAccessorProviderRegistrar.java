// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public final class GenerateAccessorProviderRegistrar {

  public final static ExtensionPointName<NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>>> EP_NAME = ExtensionPointName.create("com.intellij.generateAccessorProvider");

  protected synchronized static List<EncapsulatableClassMember> getEncapsulatableClassMembers(final PsiClass psiClass) {
    return ContainerUtil.concat(EP_NAME.getExtensionList(), s -> s.fun(psiClass));
  }
}
