/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class FileReferenceUtil {
  private FileReferenceUtil() {
  }

  /**
   * Returns a PsiFile element referenced to
   *
   * @param element some PsiElement
   * @return a PsiFile element referenced to
   * @see FileReference
   * @see com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
   */
  @Nullable
  public static PsiFile findFile(@Nullable PsiElement element) {
    return element == null ? null : findFile(element.getReferences());
  }

  /**
   * Iterates all references starting from the end and looking for FileReference,
   * when returns <code>resolve()</code> on it.
   *
   * @param references references, typically from PsiElement.getReferences()
   * @return PsiFile if the last FileReference resolves into a real file.
   * @see com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
   * @see com.intellij.psi.PsiElement#getReferences() 
   */
  @Nullable
  public static PsiFile findFile(PsiReference...references) {
    for (int i = references.length - 1; i >= 0; i--) {
      PsiReference ref = references[i];
      if (ref instanceof PsiDynaReference) {
        ref = ((PsiDynaReference)ref).getLastFileReference();
      }
      if (ref instanceof FileReference) {
        final PsiElement file = references[i].resolve();
        return file instanceof PsiFile ? (PsiFile)file : null;
      }
    }
    return null;
  }
}
