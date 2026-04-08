// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class TryWithResourcesPostfixTemplate extends PostfixTemplate implements DumbAware {
  protected TryWithResourcesPostfixTemplate() {
    super("twr", "try(Type f = new Type()) catch (Exception e)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement element, @NotNull Document copyDocument, int newOffset) {
    if (JavaPostfixTemplatesUtils.isInExpressionFile(element)) return false;

    if (!PsiUtil.isAvailable(JavaFeature.TRY_WITH_RESOURCES, element)) return false;

    PsiExpression initializer = JavaPostfixTemplatesUtils.getTopmostExpression(element);

    if (initializer == null) return false;

    return DumbService.getInstance(initializer.getProject()).computeWithAlternativeResolveEnabled(() -> {
      if (!(initializer.getType() instanceof PsiClassType classType)) return false;
      final PsiClass aClass = classType.resolve();
      Project project = element.getProject();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiClass autoCloseable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
      return InheritanceUtil.isInheritorOrSelf(aClass, autoCloseable, true);
    });
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }


  @Override
  public PostfixModExpander createModExpander() {
    return (ActionContext actionContext, PostfixTemplateProvider provider, TextRange keyRange) ->
      ModCommand.psiUpdate(actionContext.withSelection(new TextRange(keyRange.getStartOffset(), keyRange.getStartOffset()))
                             .withOffset(keyRange.getStartOffset()),
                           document -> document.deleteString(actionContext.selection().getStartOffset(), actionContext.selection().getEndOffset()),
                           updater -> {
                             updater.getDocument().deleteString(PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()), actionContext.selection().getStartOffset());
                             PsiDocumentManager.getInstance(actionContext.project()).commitDocument(updater.getDocument());
                             PsiFile file = updater.getPsiFile();
                             provider.preCheckModCommand(file, PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset()));
                             PsiElement context =
                               CustomTemplateCallback.getContext(file, PostfixLiveTemplate.positiveOffset(keyRange.getStartOffset() - 1));

                             PsiExpression expression = JavaPostfixTemplatesUtils.getTopmostExpression(context);
                             assert expression != null;

                             Project project = context.getProject();

                             updater.getDocument().deleteString(expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset());

                             TemplateManager manager = TemplateManager.getInstance(project);
                             TemplateImpl template = (TemplateImpl)manager.createTemplate("", "");

                             buildTemplate(template, expression, project);
                             TemplateManagerImpl.updateTemplate(template, updater);
                           });
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    assert expression != null;

    Project project = context.getProject();

    editor.getDocument().deleteString(expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    Template template = manager.createTemplate("", "");
    buildTemplate(template, expression, project);

    manager.startTemplate(editor, template);
  }

  private static void buildTemplate(@NotNull Template template,
                                    @NotNull PsiExpression expression,
                                    @NotNull Project project) {
    template.setToReformat(true);
    template.addTextSegment("try (");
    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());

    DumbService dumbService = DumbService.getInstance(project);
    if (Boolean.TRUE.equals(JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE) &&
        PsiUtil.isAvailable(JavaFeature.LVTI, expression)) {
      template.addVariable("type", new TextExpression(JavaKeywords.VAR), false);
    }
    else {
      PsiType type = dumbService.computeWithAlternativeResolveEnabled(expression::getType);
      template.addVariable("type", new TypeExpression(project, new PsiType[]{type}), false);
    }
    template.addTextSegment(" ");
    template.addVariable("name", name, name, true);
    template.addTextSegment(" = ");
    template.addVariable("variable", new TextExpression(expression.getText()), false);
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    Collection<PsiClassType> unhandled = dumbService.computeWithAlternativeResolveEnabled(() -> getUnhandled(expression));
    for (PsiClassType exception : unhandled) {
      MacroCallNode variable = new MacroCallNode(new SuggestVariableNameMacro());
      template.addTextSegment("catch(");
      template.addVariable("type " + exception.getClassName(), new TypeExpression(project, new PsiType[]{exception}), false);
      template.addTextSegment(" ");
      template.addVariable("name " + exception.getClassName(), variable, variable, false);
      template.addTextSegment(") {}");
    }
  }

  private static @NotNull Collection<PsiClassType> getUnhandled(@NotNull PsiExpression expression) {
    assert expression.getType() != null;
    return ExceptionUtil.getUnhandledCloserExceptions(expression, null, expression.getType());
  }
}
