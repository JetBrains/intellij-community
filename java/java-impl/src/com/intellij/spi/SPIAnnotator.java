/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.spi.psi.SPIClassProviderReferenceElement;
import org.jetbrains.annotations.NotNull;

public class SPIAnnotator implements Annotator{
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
        else if (resolve instanceof PsiClass && psiClass != null) {
          if (!((PsiClass)resolve).isInheritor(psiClass, true)) {
            holder.newAnnotation(HighlightSeverity.ERROR, JavaBundle.message("spi.extension.error.message", serviceProviderName)).create();
          }
        }
      }
    }
  }
}
