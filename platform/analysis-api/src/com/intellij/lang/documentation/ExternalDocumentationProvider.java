// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.documentation;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ExternalDocumentationProvider {
  @Nullable
  String fetchExternalDocumentation(final Project project, PsiElement element, final List<String> docUrls);

  /**
   * Quick check for existence of external documentation for specified element. Called from action update, so must be fast.
   * If not implemented, update check is performed by calling {@link DocumentationProvider#getUrlFor(PsiElement, PsiElement)}.
   *
   * @deprecated existing implementations fall back to checking for existing url
   * @param element the element to check for existence of documentation
   * @param originalElement the element at caret (on which the action was invoked)
   * @return true if the external documentation action should be enabled, false otherwise.
   */
  @Deprecated
  boolean hasDocumentationFor(PsiElement element, PsiElement originalElement);

  /**
   * Checks if the provider is capable of asking the user to configure external documentation for an element.
   *
   * @param element the element for which no documentation was found
   * @return true if the element is applicable to this provider and the provider has a UI to configure the documentation location, false otherwise
   */
  boolean canPromptToConfigureDocumentation(PsiElement element);

  /**
   * Prompts the user to configure the external documentation for an element if none was found.
   *
   * @param element the element for which no documentation was found
   */
  void promptToConfigureDocumentation(PsiElement element);
}
