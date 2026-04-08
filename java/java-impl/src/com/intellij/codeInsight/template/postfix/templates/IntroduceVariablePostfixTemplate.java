// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableHandlerBase;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableService;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  private static final PostfixTemplateExpressionSelector MY_SELECTOR =
    selectorAllExpressionsWithCurrentOffset(IS_NON_VOID);

  public IntroduceVariablePostfixTemplate() {
    super(null, "var", "T name = expr", MY_SELECTOR, null);
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    JavaIntroduceVariableHandlerBase handler = (JavaIntroduceVariableHandlerBase)LanguageRefactoringSupport.getInstance()
      .forLanguage(JavaLanguage.INSTANCE)
      .getIntroduceVariableHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }

  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return (ActionContext actionContext, PostfixTemplateProvider provider, TextRange keyRange) -> {
      Project project = actionContext.project();
      List<PsiElement> expressions = PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> {
      PsiFile copyFile = (PsiFile)actionContext.file().copy();
      Document copyDocument = copyFile.getFileDocument();
      int startOffset = PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset());
      copyDocument.deleteString(startOffset, keyRange.getEndOffset());
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      provider.preCheckModCommand(copyFile, startOffset);
      PsiDocumentManager.getInstance(project).commitDocument(copyDocument);
      PsiElement context = CustomTemplateCallback.getContext(copyFile, PostfixLiveTemplate.positiveOffset(startOffset));
      return MY_SELECTOR.getExpressions(context, copyFile.getFileDocument(), startOffset);
      });
      if (expressions.isEmpty()) return ModCommand.nop();

      if (expressions.size() == 1) {
        return buildCommandWithOccurrenceChoice(actionContext, keyRange, expressions.getFirst(), provider, true);
      }

      List<ModCommandAction> actions = ContainerUtil.mapNotNull(expressions, expr -> {
        //noinspection HardCodedStringLiteral
        String title = MY_SELECTOR.getRenderer().fun(expr);
        return new ModCommandAction() {
          @Override
          public @NotNull Presentation getPresentation(@NotNull ActionContext ctx) {
            return Presentation.of(title);
          }

          @Override
          public @NotNull ModCommand perform(@NotNull ActionContext ctx) {
            return buildCommandWithOccurrenceChoice(ctx, new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()), expr, provider, false);
          }

          @Override
          public @NotNull String getFamilyName() {
            return title;
          }
        };
      });
      if (actions.isEmpty()) return ModCommand.nop();
      return ModCommand.chooseAction(CodeInsightBundle.message("dialog.title.expressions"), actions);
    };
  }

  private static @NotNull ModCommand buildCommandWithOccurrenceChoice(@NotNull ActionContext ctx,
                                                                      @NotNull TextRange keyRange,
                                                                      @NotNull PsiElement virtualExpr,
                                                                      @NotNull PostfixTemplateProvider provider,
                                                                      boolean chooseReplace) {
    JavaIntroduceVariableService service = JavaIntroduceVariableService.getInstance();
    List<TextRange> ranges = service.getOccurrences((PsiExpression)virtualExpr);
    if (ranges.size() <= 1 || !chooseReplace) {
      return createIntroduceCommand(ctx, keyRange, virtualExpr, false, provider);
    }
    return ModCommand.chooseAction(
      RefactoringBundle.message("replace.multiple.occurrences.found"),
      List.of(
        createReplaceAction(RefactoringBundle.message("replace.this.occurrence.only"), ctx,
                            keyRange, virtualExpr,
                            List.of(virtualExpr.getTextRange()), provider),
        createReplaceAction(RefactoringBundle.message("replace.all.occurrences", ranges.size()), ctx,
                            keyRange,
                            virtualExpr, ranges, provider)
      ));
  }

  private static @NotNull ModCommand createIntroduceCommand(@NotNull ActionContext ctx,
                                                            @NotNull TextRange keyRange,
                                                            @NotNull PsiElement virtualExpr,
                                                            boolean replaceAll,
                                                            @NotNull PostfixTemplateProvider provider) {
    TextRange newSelection = new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset());
    ActionContext updatedContext = ctx.withSelection(newSelection).withOffset(keyRange.getStartOffset());
    ModCommand command = ModCommand.psiUpdate(updatedContext,
                                              document -> {
                                                document.deleteString(ctx.selection().getStartOffset(), ctx.selection().getEndOffset());
                                              },
                                              updater -> {
                                                updater.getDocument()
                                                  .deleteString(PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()), ctx.selection().getStartOffset());
                                                PsiDocumentManager.getInstance(ctx.project()).commitDocument(updater.getDocument());
                                                provider.preCheckModCommand(updater.getPsiFile(), PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()));
                                                PsiElement expression =
                                                  PsiTreeUtil.findSameElementInCopy(virtualExpr, updater.getPsiFile());
                                                expression = ElementToWorkOn.getWritable(expression, updater);
                                                PsiVariable variable = JavaIntroduceVariableService.getInstance()
                                                  .introduceVariable((PsiExpression)expression, replaceAll);
                                                if (variable != null) {
                                                  updater.rename(variable, new VariableNameGenerator(variable, VariableKind.LOCAL_VARIABLE)
                                                    .byExpression(variable.getInitializer())
                                                    .byType(variable.getType())
                                                    .generateAll(true));
                                                }
                                              });
    return command;
  }

  private static @NotNull ModCommandAction createReplaceAction(@NotNull @NlsSafe String title,
                                                               @NotNull ActionContext ctx,
                                                               @NotNull TextRange key,
                                                               @NotNull PsiElement virtualExpr,
                                                               @NotNull List<TextRange> ranges,
                                                               @NotNull PostfixTemplateProvider provider) {
    return new ModCommandAction() {
      @Override
      public @NotNull Presentation getPresentation(@NotNull ActionContext c) {
        return Presentation.of(title).withHighlighting(ranges.toArray(TextRange.EMPTY_ARRAY));
      }

      @Override
      public @NotNull ModCommand perform(@NotNull ActionContext c) {
        return createIntroduceCommand(c, key, virtualExpr, ranges.size() > 1, provider);
      }

      @Override
      public @NotNull String getFamilyName() {
        return title;
      }
    };
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context,
                              @NotNull Document copyDocument, int newOffset) {
    // Non-inplace mode would require a modal dialog, which is not allowed under postfix templates
    EditorSettingsExternalizable editorSettingsExternalizable = EditorSettingsExternalizable.getInstance();
    return (editorSettingsExternalizable == null ||
            editorSettingsExternalizable.isVariableInplaceRenameEnabled()) &&
           super.isApplicable(context, copyDocument, newOffset) &&
           !JavaPostfixTemplatesUtils.isInExpressionFile(context);
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}
