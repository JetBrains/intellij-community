// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixModExpander;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public class EditableTemplateModExpander implements PostfixModExpander {
  private final @NotNull EditablePostfixTemplate myTemplate;

  public EditableTemplateModExpander(@NotNull EditablePostfixTemplate template) {
    myTemplate = template;
  }

  @Override
  public @NotNull ModCommand expand(@NotNull ActionContext actionContext,
                                    @NotNull PostfixTemplateProvider provider,
                                    @NotNull TextRange keyRange) {
    Project project = actionContext.project();
    List<PsiElement> virtualExpressions = PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> {
      PsiFile copyFile = (PsiFile)actionContext.file().copy();
      Document copyDocument = copyFile.getFileDocument();
      int startOffset = keyRange.getStartOffset();
      startOffset = PostfixLiveTemplate.positiveOffset(startOffset);
      copyDocument.deleteString(startOffset, keyRange.getEndOffset());
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      provider.preCheckModCommand(copyFile, startOffset);
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      PsiElement context = CustomTemplateCallback.getContext(copyFile, PostfixLiveTemplate.positiveOffset(startOffset));
      return myTemplate.getExpressions(context, context.getContainingFile().getFileDocument(), startOffset);
    });
    if (virtualExpressions.isEmpty()) {
      return ModCommand.nop();
    }

    if (virtualExpressions.size() == 1) {
      return createModCommand(actionContext, keyRange, virtualExpressions.getFirst(), provider);
    }

    List<ModCommandAction> actions = ContainerUtil.mapNotNull(
      virtualExpressions,
      expr -> buildExpandModAction(expr, myTemplate.getElementRenderer().fun(expr),
                                   new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()), provider));
    if (actions.isEmpty()) {
      return ModCommand.nop();
    }
    return ModCommand.chooseAction(CodeInsightBundle.message("dialog.title.expressions"), actions);
  }

  @SuppressWarnings("HardCodedStringLiteral") // expression text is used as chooser item title
  private @NotNull ModCommandAction buildExpandModAction(@NotNull PsiElement virtualExpression,
                                                         @NotNull String title,
                                                         @NotNull TextRange key,
                                                         @NotNull PostfixTemplateProvider provider) {
    return new ModCommandAction() {
      @Override
      public @NotNull Presentation getPresentation(@NotNull ActionContext ctx) {
        return Presentation.of(title).withHighlighting(virtualExpression.getTextRange());
      }

      @Override
      public @NotNull ModCommand perform(@NotNull ActionContext ctx) {
        return createModCommand(ctx, key, virtualExpression, provider);
      }

      @Override
      public @NotNull String getFamilyName() {
        return title;
      }
    };
  }

  private @NotNull ModCommand createModCommand(@NotNull ActionContext ctx, @NotNull TextRange key,
                                               @NotNull PsiElement virtualExpression,
                                               @NotNull PostfixTemplateProvider provider) {
    return ModCommand.psiUpdate(ctx.withSelection(new TextRange(key.getStartOffset(), key.getStartOffset())).withOffset(key.getStartOffset()),
                                document -> document.deleteString(ctx.selection().getStartOffset(), ctx.selection().getEndOffset()),
                                updater -> {
                                  updater.getDocument().deleteString(PostfixLiveTemplate.positiveOffset(key.getStartOffset()), ctx.selection().getStartOffset());
                                  PsiDocumentManager.getInstance(ctx.project()).commitDocument(updater.getDocument());
                                  provider.preCheckModCommand(updater.getPsiFile(), PostfixLiveTemplate.positiveOffset(key.getStartOffset()));
                                  String exprText = virtualExpression.getText();
                                  PsiElement expression = PsiTreeUtil.findSameElementInCopy(virtualExpression, updater.getPsiFile());
                                  TextRange rangeToRemove = myTemplate.getRangeToRemove(expression);
                                  TemplateImpl template = myTemplate.getLiveTemplate().copy();
                                  updater.getDocument().deleteString(rangeToRemove.getStartOffset(), rangeToRemove.getEndOffset());
                                  template.addVariable("EXPR", new TextExpression(exprText), false);
                                  myTemplate.addTemplateVariables(expression, template);
                                  TemplateManagerImpl.updateTemplate(template, updater);
                                });
  }
}