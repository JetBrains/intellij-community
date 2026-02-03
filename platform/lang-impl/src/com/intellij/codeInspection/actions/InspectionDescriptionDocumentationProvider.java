
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.InspectionNodeInfo;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class InspectionDescriptionDocumentationProvider extends AbstractDocumentationProvider {
  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof InspectionElement)) {
      return null;
    }

    InspectionToolWrapper toolWrapper = ((InspectionElement)element).getToolWrapper();
    String description = toolWrapper.loadDescription();
    if (description == null) {
      return null;
    }
    return DocumentationMarkup.DEFINITION_START + StringUtil.escapeXmlEntities(toolWrapper.getDisplayName()) + DocumentationMarkup.DEFINITION_END +
           DocumentationMarkup.CONTENT_START +
           InspectionNodeInfo.stripUIRefsFromInspectionDescription(description) +
           DocumentationMarkup.CONTENT_END;
  }
}
