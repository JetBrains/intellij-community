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
package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.lang.findUsages.LanguageFindUsages;

/**
 * @author peter
*/
public final class DefaultFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  public boolean canFindUsages(final PsiElement element) {
    if (element instanceof PsiFile) {
      if (((PsiFile)element).getVirtualFile() == null) return false;
    }
    else if (!LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage()).canFindUsagesFor(element)) {
      return false;
    }
    return element.isValid();
  }

  public FindUsagesHandler createFindUsagesHandler(final PsiElement element, final boolean forHighlightUsages) {
    if (canFindUsages(element)) {
      return new FindUsagesHandler(element){};
    }
    return null;
  }
}
