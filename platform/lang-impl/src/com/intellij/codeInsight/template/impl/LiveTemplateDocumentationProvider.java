// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LiveTemplateDocumentationProvider extends AbstractDocumentationProvider {
  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (object instanceof LiveTemplateLookupElementImpl) {
      TemplateImpl template = ((LiveTemplateLookupElementImpl)object).getTemplate();
      final TemplateImpl templateFromSettings = TemplateSettings.getInstance().getTemplate(template.getKey(), template.getGroupName());
      if (templateFromSettings != null) {
        return new LiveTemplateElement(templateFromSettings, psiManager);
      }
    }
    return null;
  }

  @Override
  public @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof LiveTemplateElement)) {
      return null;
    }

    TemplateImpl template = ((LiveTemplateElement)element).getTemplate();
    return DocumentationMarkup.DEFINITION_START + StringUtil.escapeXmlEntities(template.getString()) + DocumentationMarkup.DEFINITION_END +
           DocumentationMarkup.CONTENT_START + StringUtil.escapeXmlEntities(StringUtil.notNullize(template.getDescription())) +
           DocumentationMarkup.CONTENT_END;
  }

  private static class LiveTemplateElement extends FakePsiElement {
    private final @NotNull TemplateImpl myTemplate;
    private final @NotNull PsiManager myPsiManager;
    private final @NotNull DummyHolder myDummyHolder;

    LiveTemplateElement(@NotNull TemplateImpl template, @NotNull PsiManager psiManager) {
      myTemplate = template;
      myPsiManager = psiManager;
      myDummyHolder = DummyHolderFactory.createHolder(myPsiManager, null);
    }

    public @NotNull TemplateImpl getTemplate() {
      return myTemplate;
    }

    @Override
    public PsiElement getParent() {
      return myDummyHolder;
    }

    @Override
    public ItemPresentation getPresentation() {
      return new ItemPresentation() {
        @Override
        public @Nullable String getPresentableText() {
          return myTemplate.getKey();
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
          return null;
        }
      };
    }

    @Override
    public PsiManager getManager() {
      return myPsiManager;
    }
  }
}
