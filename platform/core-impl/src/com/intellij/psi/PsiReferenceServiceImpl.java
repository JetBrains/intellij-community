// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class PsiReferenceServiceImpl extends PsiReferenceService {

  private static final Logger LOG = Logger.getInstance(PsiReferenceService.class);

  @NotNull
  @Override
  public List<PsiReference> getReferences(@NotNull PsiElement element, @NotNull Hints hints) {
    List<PsiReference> references = doGetReferences(element, hints);
    assertReferencesHaveSameElement(element, references);
    return references;
  }

  @NotNull
  private static List<PsiReference> doGetReferences(@NotNull PsiElement element, @NotNull Hints hints) {
    if (element instanceof ContributedReferenceHost) {
      return Arrays.asList(ReferenceProvidersRegistry.getReferencesFromProviders(element, hints));
    }
    if (element instanceof HintedReferenceHost) {
      return Arrays.asList(((HintedReferenceHost)element).getReferences(hints));
    }
    return Arrays.asList(element.getReferences());
  }

  private static void assertReferencesHaveSameElement(@NotNull PsiElement element, @NotNull List<PsiReference> references) {
    for (PsiReference reference : references) {
      PsiElement referenceElement = reference.getElement();
      if (referenceElement != element) {
        LOG.error(
          "Reference element is not the same element for which references were queried",
          new RuntimeException(
            "Element: " + element.getClass().getName() + "; " +
            "reference: " + reference.getClass().getName() + "; " +
            "reference element: " + referenceElement.getClass().getName()
          ),
          new Attachment(
            "info.txt",
            "Element text: `" + element.getText() + "`\n" +
            "Reference range: " + reference.getRangeInElement()
          )
        );
      }
    }
  }
}
