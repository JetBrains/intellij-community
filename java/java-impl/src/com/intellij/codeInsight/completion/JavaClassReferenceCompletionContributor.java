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
