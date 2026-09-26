// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.icons.AllIcons;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.modcompletion.PsiUpdateCompletionItem;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.Map;

/**
 * A simplified mirror of {@link LiveTemplateCompletionContributor} to provide live templates as {@link ModCompletionItem}s. 
 */
@NotNullByDefault
final class LiveTemplateModCompletionItemProvider implements ModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (!LiveTemplateCompletionContributor.shouldShowAllTemplates()) return;
    PsiFile file = context.getPosition().getContainingFile();
    int offset = context.offset();
    List<TemplateImpl> availableTemplates = TemplateManagerImpl.listApplicableTemplates(
      TemplateActionContext.expanding(file, offset - context.prefix().length()));
    Map<TemplateImpl, String> templates =
      ListTemplatesHandler.filterTemplatesByPrefix(availableTemplates, file.getFileDocument(), offset, false, false);
    for (Map.Entry<TemplateImpl, String> entry : templates.entrySet()) {
      sink.accept(new LiveTemplateModCompletionItem(entry.getKey()));
    }
  }

  private static class LiveTemplateModCompletionItem extends PsiUpdateCompletionItem<TemplateImpl> {
    private LiveTemplateModCompletionItem(TemplateImpl template) {
      super(template.getKey(), template);
    }

    @Override
    public void update(ActionContext actionContext, InsertionContext insertionContext, ModPsiUpdater updater) {
      updater.getDocument().deleteString(actionContext.selection().getStartOffset(), actionContext.selection().getEndOffset());
      TemplateManagerImpl.updateTemplate(contextObject(), updater);
    }

    @Override
    public ModCompletionItemPresentation presentation() {
      String description = contextObject().getDescription();
      return new ModCompletionItemPresentation(MarkupText.plainText(contextObject().getKey()))
        .withMainIcon(() -> AllIcons.Nodes.Template)
        .withDetailText(description == null ? MarkupText.empty() : MarkupText.plainText(description));
    }
  }
}
