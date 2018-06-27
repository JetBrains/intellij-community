// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deadCode.jvm;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.lang.jvm.JvmElement;
import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.source.JvmDeclarationSearch;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class JvmUnusedDeclarationLineMarkerProvider implements LineMarkerProvider {
  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    for (JvmElement e: JvmDeclarationSearch.getElementsByIdentifier(element)) {
      if (e instanceof JvmMethod) {
        if (!JvmDeadCodeSearcher.isDirectlyUsed(e.getSourceElement(), e)) {
          //TODO
        }
      }
    }

    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {

  }
}
