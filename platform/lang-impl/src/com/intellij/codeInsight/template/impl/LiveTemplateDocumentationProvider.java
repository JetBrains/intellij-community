/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
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
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (!(element instanceof LiveTemplateElement)) {
      return null;
    }

    TemplateImpl template = ((LiveTemplateElement)element).getTemplate();
    StringBuilder doc = new StringBuilder("<h2>").append(template.getKey()).append("</h2>");
    String description = template.getDescription();
    if (StringUtil.isNotEmpty(description)) {
      doc.append("<pre>").append(StringUtil.escapeXml(description)).append("</pre><h2>template text</h2>");
    }
    return doc.append("<pre>").append(StringUtil.escapeXml(template.getString())).append("</pre>").toString();
  }

  private static class LiveTemplateElement extends FakePsiElement {
    @NotNull private final TemplateImpl myTemplate;
    @NotNull private final PsiManager myPsiManager;
    @NotNull private DummyHolder myDummyHolder;

    public LiveTemplateElement(@NotNull TemplateImpl template, @NotNull PsiManager psiManager) {
      myTemplate = template;
      myPsiManager = psiManager;
      myDummyHolder = DummyHolderFactory.createHolder(myPsiManager, null);
    }

    @NotNull
    public TemplateImpl getTemplate() {
      return myTemplate;
    }

    @Override
    public PsiElement getParent() {
      return myDummyHolder;
    }

    @Override
    public ItemPresentation getPresentation() {
      return new ItemPresentation() {
        @Nullable
        @Override
        public String getPresentableText() {
          return myTemplate.getKey();
        }

        @Nullable
        @Override
        public String getLocationString() {
          return null;
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
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
