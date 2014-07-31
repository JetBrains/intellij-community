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
package com.intellij.codeInspection.xml;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class DeprecatedClassUsageInspection extends XmlSuppressableInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        if (tag.getValue().getTextElements().length > 0) {
          checkReferences(tag, holder);
        }
      }

      @Override
      public void visitXmlAttributeValue(XmlAttributeValue value) {
        checkReferences(value, holder);
      }
    };
  }

  private static void checkReferences(PsiElement psiElement, ProblemsHolder holder) {
    PsiReference[] references = psiElement.getReferences();
    PsiReference last = ArrayUtil.getLastElement(references);
    if (last != null && (!(last instanceof ResolvingHint) || ((ResolvingHint)last).canResolveTo(PsiDocCommentOwner.class))) {
      PsiElement resolve = last.resolve();
      DeprecationInspection.checkDeprecated(resolve, psiElement, last.getRangeInElement(), holder);
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "XML";
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Deprecated API usage in XML";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "DeprecatedClassUsageInspection";
  }
}
