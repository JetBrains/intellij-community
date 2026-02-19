// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spi;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.java.JavaBundle;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.spi.psi.SPIClassProviderReferenceElement;
import org.jetbrains.annotations.NotNull;

public final class SPIAnnotator implements Annotator{
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file != null && file.getFileType() instanceof SPIFileType) {
      final String serviceProviderName = file.getName();
      final PsiClass psiClass =
        ClassUtil.findPsiClass(element.getManager(), serviceProviderName, null, true, element.getContainingFile().getResolveScope());
      if (element instanceof PsiFile) {
        if (psiClass == null) {
          holder.newAnnotation(HighlightSeverity.ERROR, JavaBundle.message("spi.no.provider.error.message", serviceProviderName)).fileLevel().create();
        }
      }
      else if (element instanceof SPIClassProviderReferenceElement) {
        final PsiElement resolve = ((SPIClassProviderReferenceElement)element).resolve();
        if (resolve == null) {
          holder.newAnnotation(HighlightSeverity.ERROR, AnalysisBundle.message("cannot.resolve.symbol", element.getText())).create();
        }
        else if (resolve instanceof PsiClass && psiClass != null && !InheritanceUtil.isInheritorOrSelf((PsiClass)resolve, psiClass, true)) {
          holder.newAnnotation(HighlightSeverity.ERROR, JavaBundle.message("spi.extension.error.message", serviceProviderName)).create();
        }
      }
    }
  }
}
