// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInspection.jigsaw.JigsawUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class JigsawCompletionContributor extends CompletionContributor {
  public JigsawCompletionContributor() {
    extend(CompletionType.BASIC, PsiJavaPatterns.psiElement(PsiIdentifier.class)
             .inside(PsiClass.class)
             .withLanguage(JavaLanguage.INSTANCE), new CompletionProvider<>() {
             @Override
             public void addCompletions(@NotNull CompletionParameters parameters,
                                        @NotNull ProcessingContext context,
                                        @NotNull CompletionResultSet resultSet) {
               int offset = parameters.getOffset() - 1;
               if (isInvalidOffset(parameters.getOriginalFile(), offset)) return;
               TemplateActionContext templateActionContext = TemplateActionContext.create(parameters.getOriginalFile(),
                                                                                          parameters.getEditor(),
                                                                                          offset,
                                                                                          offset, false);
               JavaCodeContextType declaration = TemplateContextTypes.getByClass(JavaCodeContextType.Declaration.class);
               if (!declaration.isInContext(templateActionContext)) return;

               PsiElement element = parameters.getPosition();
               PsiClass targetClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

               if (!JigsawUtil.checkProviderMethodAccessible(targetClass)) return;

               String className = targetClass.getName();
               if (className == null) return;

               resultSet.addElement(new JigsawProviderLookupElement(targetClass));
             }

             private static boolean isInvalidOffset(@NotNull PsiFile file, int offset) {
               PsiElement element = file.findElementAt(offset);
               return element == null || !element.getTextRange().contains(offset);
             }
           }
    );
  }
}
