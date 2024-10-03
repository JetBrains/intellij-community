// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Gregory.Shrago
 */
@ApiStatus.Internal
public class PsiReferenceServiceImpl extends PsiReferenceService {

  private static final Logger LOG = Logger.getInstance(PsiReferenceService.class);

  @Override
  public @NotNull List<PsiReference> getReferences(@NotNull PsiElement element, @NotNull Hints hints) {
    List<PsiReference> references = doGetReferences(element, hints);
    assertReferencesHaveSameElement(element, references);
    return references;
  }

  private static @NotNull List<PsiReference> doGetReferences(@NotNull PsiElement element, @NotNull Hints hints) {
    if (element instanceof ContributedReferenceHost) {
      return Arrays.asList(ReferenceProvidersRegistry.getReferencesFromProviders(element, hints));
    }
    if (element instanceof HintedReferenceHost) {
      return Arrays.asList(((HintedReferenceHost)element).getReferences(hints));
    }
    return Arrays.asList(element.getReferences());
  }

  private static final Set<String> ourReportedReferenceClasses = ContainerUtil.newConcurrentSet();

  private static void assertReferencesHaveSameElement(@NotNull PsiElement element, @NotNull List<? extends PsiReference> references) {
    for (PsiReference reference : references) {
      PsiElement referenceElement = reference.getElement();
      if (referenceElement == element) {
        continue;
      }
      Class<? extends PsiReference> referenceClass = reference.getClass();
      if (!ourReportedReferenceClasses.add(referenceClass.getName())) {
        continue;
      }
      PluginException.logPluginError(
        LOG,
        "Reference element is not the same element for which references were queried",
        new RuntimeExceptionWithAttachments(
          "Element: " + element.getClass().getName() + "; " +
          "reference: " + referenceClass.getName() + "; " +
          "reference element: " + referenceElement.getClass().getName(),
          new Attachment(
            "info.txt",
            "Element text: `" + element.getText() + "`\n" +
            "Reference range: " + reference.getRangeInElement()
          )
        ),
        referenceClass
      );
    }
  }
}
