// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jvm;

import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.jvm.source.JvmDeclarationSearch.getElementsByIdentifier;

public abstract class JvmRunLineMarkerContributor extends RunLineMarkerContributor {

  @Nullable
  @Override
  public final Info getInfo(@NotNull PsiElement element) {
    if (!Registry.is("ide.jvm.run.marker")) return null;
    for (JvmElement declaration : getElementsByIdentifier(element)) {
      Info info = getInfo(declaration);
      if (info != null) {
        return info;
      }
    }
    return null;
  }

  @Nullable
  protected abstract Info getInfo(JvmElement element);
}
