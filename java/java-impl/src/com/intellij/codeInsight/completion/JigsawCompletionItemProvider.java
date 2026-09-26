// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInspection.jigsaw.JigsawUtil;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
public class JigsawCompletionItemProvider implements ModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (!context.isBasic()) return;
    int offset = context.getOffset() - 1;
    if (isInvalidOffset(context.getOriginalFile(), offset)) return;
    TemplateActionContext templateActionContext = TemplateActionContext.create(context.getOriginalFile(),
                                                                               null,
                                                                               offset,
                                                                               offset, false);
    JavaCodeContextType declaration = TemplateContextTypes.getByClass(JavaCodeContextType.Declaration.class);
    if (!declaration.isInContext(templateActionContext)) return;

    PsiElement element = context.getPosition();
    PsiClass targetClass = PsiUtil.getContainingClass(element);

    if (!JigsawUtil.checkProviderMethodAccessible(targetClass)) return;

    String className = targetClass.getName();
    if (className == null) return;

    ModCompletionItemPresentation presentation = new ModCompletionItemPresentation(MarkupText.plainText(PsiJavaModule.PROVIDER_METHOD))
      .withDetailText(MarkupText.plainText(JavaBundle.message("completion.provider.method.declaration.type")))
      .withMainIcon(() -> AllIcons.Nodes.Method);
    sink.accept(new CommonCompletionItem(PsiJavaModule.PROVIDER_METHOD)
                  .withPresentation(presentation)
                  .withAdditionalUpdater((completionStart, updater) -> {
                    updater.getDocument().replaceString(completionStart, updater.getCaretOffset(), "");
                    JigsawUtil.addProviderMethod(targetClass, updater, updater.getCaretOffset());
                  }));
  }

  private static boolean isInvalidOffset(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    return element == null || !element.getTextRange().contains(offset);
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
