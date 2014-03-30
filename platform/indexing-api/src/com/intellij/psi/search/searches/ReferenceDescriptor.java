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
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

/**
* @author max
*/
public class ReferenceDescriptor {
  public static final Function<PsiReference, ReferenceDescriptor> MAPPER = new Function<PsiReference, ReferenceDescriptor>() {
    @Override
    public ReferenceDescriptor fun(PsiReference psiReference) {
      final PsiElement element = psiReference.getElement();
      final PsiFile file = element.getContainingFile();
      return new ReferenceDescriptor(file, element.getTextRange().getStartOffset() + psiReference.getRangeInElement().getStartOffset());
    }
  };
  private final PsiFile file;
  private final int offset;

  ReferenceDescriptor(@NotNull PsiFile file, int offset) {
    this.file = file;
    this.offset = offset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReferenceDescriptor)) return false;

    ReferenceDescriptor that = (ReferenceDescriptor)o;

    if (offset != that.offset) return false;
    return file.equals(that.file);
  }

  @Override
  public int hashCode() {
    return 31 * file.hashCode() + offset;
  }
}
