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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;

/**
 * @author peter
 */
public class FileReferenceCharFilter extends CharFilter{
  public Result acceptChar(char c, int prefixLength, Lookup lookup) {
    final PsiFile file = lookup.getPsiFile();
    if (file == null) {
      return null;
    }

    final LookupElement item = lookup.getCurrentItem();
    if (item != null && item.getObject() instanceof PsiFileSystemItem) {
      if ('.' == c) return Result.ADD_TO_PREFIX;

      final PsiReference reference = file.findReferenceAt(lookup.getEditor().getCaretModel().getOffset());
      if (reference instanceof FileReference) return Result.HIDE_LOOKUP;

      if (reference instanceof PsiMultiReference) {
        for (PsiReference psiReference : ((PsiMultiReference)reference).getReferences()) {
          if (psiReference instanceof FileReference) return Result.HIDE_LOOKUP;
        }
      }

    }


    return null;
  }
}
