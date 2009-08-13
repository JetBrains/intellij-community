/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaClassReferenceCompletionContributor extends CompletionContributor {

  public void beforeCompletion(@NotNull final CompletionInitializationContext context) {
    final PsiFile file = context.getFile();
    final Project project = context.getProject();

    JavaCompletionUtil.initOffsets(file, project, context.getOffsetMap());

    PsiReference reference = file.findReferenceAt(context.getStartOffset());
    if (reference instanceof PsiMultiReference) {
      for (final PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
        if (psiReference instanceof JavaClassReference) {
          reference = psiReference;
          break;
        }
      }
    }
    if (reference instanceof JavaClassReference) {
      final JavaClassReference classReference = (JavaClassReference)reference;
      if (classReference.getExtendClassNames() != null) {
        final PsiReference[] references = classReference.getJavaClassReferenceSet().getReferences();
        final PsiReference lastReference = references[references.length - 1];
        final int endOffset = lastReference.getRangeInElement().getEndOffset() + lastReference.getElement().getTextRange().getStartOffset();
        context.getOffsetMap().addOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET, endOffset);
      }
    }

  }


}
