/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class SharedPsiElementImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.SharedPsiElementImplUtil");

  private SharedPsiElementImplUtil() {
  }

  @Nullable
  public static PsiReference findReferenceAt(PsiElement thisElement, int offset, @Nullable Language lang) {
    if (thisElement == null) return null;
    PsiElement element = lang != null ? thisElement.getContainingFile().getViewProvider().findElementAt(offset, lang) :
                         thisElement.findElementAt(offset);
    if (element == null || element instanceof OuterLanguageElement) return null;
    offset = thisElement.getTextRange().getStartOffset() + offset - element.getTextRange().getStartOffset();

    List<PsiReference> referencesList = new ArrayList<>();
    while (element != null) {
      addReferences(offset, element, referencesList);
      if (element instanceof PsiFile) break;
      if (element instanceof HintedReferenceHost &&
          !((HintedReferenceHost)element).shouldAskParentForReferences(new PsiReferenceService.Hints(null, offset))) {
        break;
      }
      offset = element.getStartOffsetInParent() + offset;
      element = element.getParent();
    }

    if (referencesList.isEmpty()) return null;
    if (referencesList.size() == 1) return referencesList.get(0);
    return new PsiMultiReference(referencesList.toArray(new PsiReference[referencesList.size()]),
                                 referencesList.get(referencesList.size() - 1).getElement());
  }

  @Nullable
  public static PsiReference findReferenceAt(PsiElement thisElement, int offset) {
    return findReferenceAt(thisElement, offset, null);
  }

  private static void addReferences(int offset, PsiElement element, final Collection<PsiReference> outReferences) {
    PsiReference[] references;
    if (element instanceof HintedReferenceHost) {
      references = ((HintedReferenceHost)element).getReferences(new PsiReferenceService.Hints(null, offset));
    } else {
      references = element.getReferences();
    }
    for (final PsiReference reference : references) {
      if (reference == null) {
        LOG.error("Null reference returned from " + element + " of " + element.getClass());
        continue;
      }
      for (TextRange range : ReferenceRange.getRanges(reference)) {
        LOG.assertTrue(range != null, reference);
        if (range.containsOffset(offset)) {
          outReferences.add(reference);
        }
      }
    }
  }

  @NotNull
  public static PsiReference[] getReferences(PsiElement thisElement) {
    PsiReference ref = thisElement.getReference();
    if (ref == null) return PsiReference.EMPTY_ARRAY;
    return new PsiReference[]{ref};
  }

  @Nullable
  public static PsiElement getNextSibling(PsiElement element) {
    if (element instanceof PsiFile) {
      final FileViewProvider viewProvider = ((PsiFile)element).getViewProvider();
      element = viewProvider.getPsi(viewProvider.getBaseLanguage());
    }
    if (element == null) return null;
    final PsiElement parent = element.getParent();
    if (parent == null) return null;

    final PsiElement[] children = parent.getChildren();
    final int index = getChildIndex(children, element);
    return 0 <= index && index < children.length - 1 ? children[index + 1] : null;
  }

  @Nullable
  public static PsiElement getPrevSibling(PsiElement element) {
    if (element instanceof PsiFile) {
      final FileViewProvider viewProvider = ((PsiFile)element).getViewProvider();
      element = viewProvider.getPsi(viewProvider.getBaseLanguage());
    }
    if (element == null) return null;
    final PsiElement parent = element.getParent();
    if (parent == null) return null;

    final PsiElement[] children = parent.getChildren();
    final int index = getChildIndex(children, element);
    return index > 0 ? children[index - 1] : null;
  }

  private static int getChildIndex(final PsiElement[] children, final PsiElement child) {
    for (int i = 0; i < children.length; i++) {
      PsiElement candidate = children[i];
      // do not use equals() since some smart-heads are used to override it (e.g. JspxImportStatementImpl)
      if (candidate == child) {
        return i;
      }
    }
    LOG.error("Cannot find element among its parent' children." +
              " element: '" + child + "';" +
              " parent: '" + child.getParent() + "';" +
              " children: " + Arrays.asList(children) + "; " +
              " file:" + child.getContainingFile());
    return -1;
  }

  @NotNull
  public static <VF extends VirtualFile> List<PsiFile> toPsiFiles(@NotNull PsiManager psiManager,
                                                                  @NotNull Collection<VF> virtualFiles) {
    return virtualFiles.stream()
      .map(psiManager::findFile)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
