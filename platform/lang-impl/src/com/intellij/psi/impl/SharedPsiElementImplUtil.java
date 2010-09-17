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

package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SharedPsiElementImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.SharedPsiElementImplUtil");

  private SharedPsiElementImplUtil() {
  }

  @Nullable
  public static PsiReference findReferenceAt(PsiElement thisElement, int offset, @Nullable Language lang) {
    if (thisElement == null) return null;
    PsiElement element = lang != null ? thisElement.getContainingFile().getViewProvider().findElementAt(offset, lang) :
                         thisElement.findElementAt(offset);
    if (element == null) return null;
    offset = thisElement.getTextRange().getStartOffset() + offset - element.getTextRange().getStartOffset();

    List<PsiReference> referencesList = new ArrayList<PsiReference>();
    while (element != null) {
      addReferences(offset, element, referencesList);
      offset = element.getStartOffsetInParent() + offset;
      if (element instanceof PsiFile) break;
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
    for (final PsiReference reference : element.getReferences()) {
      if (reference == null) {
        LOG.error(element);
      }
      for (TextRange range : ReferenceRange.getRanges(reference)) {
        if (range.getStartOffset() <= offset && offset < range.getEndOffset()) {
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
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    PsiElement[] children = parent.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child ==
          element) { //do not use .equals since some smartheads are used to overriding PsiElement.equals e.g. com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatementImpl.equals()
        return i < children.length - 1 ? children[i + 1] : null;
      }
    }
    LOG.error("Cannot find element among its parent' children. element: '" +
              element +
              "'; parent:'" +
              parent +
              "'; file:" +
              element.getContainingFile());
    return null;
  }

  @Nullable
  public static PsiElement getPrevSibling(PsiElement element) {
    if (element instanceof PsiFile) {
      final FileViewProvider viewProvider = ((PsiFile)element).getViewProvider();
      element = viewProvider.getPsi(viewProvider.getBaseLanguage());
    }
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    PsiElement[] children = parent.getChildren();
    for (int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      if (child ==
          element) { //do not use .equals since some smartheads are used to overriding PsiElement.equals e.g. com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatementImpl.equals()
        return i > 0 ? children[i - 1] : null;
      }
    }
    LOG.assertTrue(false);
    return null;
  }
}
