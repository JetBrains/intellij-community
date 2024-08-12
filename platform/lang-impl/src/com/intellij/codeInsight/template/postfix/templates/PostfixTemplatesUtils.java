// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.EditablePostfixTemplateWithMultipleExpressions;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixChangedBuiltinTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateExpressionCondition;
import com.intellij.lang.surroundWith.ModCommandSurrounder;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import kotlin.LazyKt;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PostfixTemplatesUtils {
  public static final String CONDITION_TAG = "condition";
  public static final String CONDITIONS_TAG = "conditions";
  public static final String TOPMOST_ATTR = "topmost";


  private PostfixTemplatesUtils() {
  }

  /**
   * @return all templates registered in the given provider, including the edited templates and builtin templates in their current state.
   */
  public static @NotNull Set<PostfixTemplate> getAvailableTemplates(@NotNull PostfixTemplateProvider provider) {
    Set<PostfixTemplate> result = new HashSet<>(provider.getTemplates());
    for (PostfixTemplate template : PostfixTemplateStorage.getInstance().getTemplates(provider)) {
      if (template instanceof PostfixChangedBuiltinTemplate) {
        result.remove(((PostfixChangedBuiltinTemplate)template).getBuiltinTemplate());
      }
      result.add(template);
    }
    return result;
  }

  /**
   * Surrounds a given expression with the provided surrounder. 
   * May execute asynchronously and return null (in this case, the selection/caret will be updated automatically).
   * @return range to select/position the caret
   */
  public static @Nullable TextRange surround(@NotNull Surrounder surrounder,
                                   @NotNull Editor editor,
                                   @NotNull PsiElement expr) {
    Project project = expr.getProject();
    PsiElement[] elements = {expr};
    if (surrounder instanceof ModCommandSurrounder modCommandSurrounder) {
      ActionContext context = ActionContext.from(editor, expr.getContainingFile());
      ReadAction.nonBlocking(
          () -> modCommandSurrounder.isApplicable(elements) ? modCommandSurrounder.surroundElements(context, elements) : null)
        .expireWhen(() -> project.isDisposed() || editor.isDisposed())
        .finishOnUiThread(ModalityState.nonModal(), command -> {
          if (command == null) {
            showErrorHint(project, editor);
          }
          else {
            CommandProcessor.getInstance().executeCommand(
              project, () -> ModCommandExecutor.getInstance().executeInteractively(context, command, editor),
              CodeInsightBundle.message("command.expand.postfix.template"),
              PostfixLiveTemplate.POSTFIX_TEMPLATE_ID);
          }
        })
        .submit(AppExecutorUtil.getAppExecutorService());
      return null;
    }
    if (surrounder.isApplicable(elements)) {
      return surrounder.surroundElements(project, editor, elements);
    }
    else {
      showErrorHint(project, editor);
    }
    return null;
  }

  public static void showErrorHint(@NotNull Project project, @NotNull Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, CodeInsightBundle.message("error.hint.can.t.expand.postfix.template"),
                                        CodeInsightBundle.message("error.hint.can.t.expand.postfix.template"), "");
  }

  /**
   * Generates a unique in the scope of a given provider template ID.
   */
  public static @NotNull String generateTemplateId(@NotNull String templateKey, @NotNull PostfixTemplateProvider provider) {
    Set<String> usedIds = new HashSet<>();
    for (PostfixTemplate builtinTemplate : provider.getTemplates()) {
      usedIds.add(builtinTemplate.getId());
    }
    for (PostfixTemplate template : PostfixTemplateStorage.getInstance().getTemplates(provider)) {
      usedIds.add(template.getId());
    }
    return UniqueNameGenerator.generateUniqueName(templateKey + "@userDefined", usedIds);
  }

  /**
   * Stores a given editable template in the given parent DOM element.
   * The given template must be an instance of {@link EditablePostfixTemplate}.
   * If the given template is {@link EditablePostfixTemplateWithMultipleExpressions},
   * then all data like usage of the topmost expression flag and expression conditions are stored.
   */
  public static void writeExternalTemplate(@NotNull PostfixTemplate template, @NotNull Element parentElement) {
    if (template instanceof EditablePostfixTemplateWithMultipleExpressions) {
      parentElement.setAttribute(TOPMOST_ATTR, String.valueOf(((EditablePostfixTemplateWithMultipleExpressions<?>)template).isUseTopmostExpression()));
      Element conditionsTag = new Element(CONDITIONS_TAG);

      //noinspection unchecked
      Set<? extends PostfixTemplateExpressionCondition<? extends PsiElement>> conditions =
        (Set<? extends PostfixTemplateExpressionCondition<? extends PsiElement>>)((EditablePostfixTemplateWithMultipleExpressions)template)
          .getExpressionConditions();

      for (PostfixTemplateExpressionCondition<? extends PsiElement> condition : conditions) {
        Element element = new Element(CONDITION_TAG);
        condition.serializeTo(element);
        conditionsTag.addContent(element);
      }
      parentElement.addContent(conditionsTag);
    }

    Element templateTag = TemplateSettings.serializeTemplate(((EditablePostfixTemplate)template).getLiveTemplate(), null,
                                                             LazyKt.lazyOf(Collections.emptyMap()));
    parentElement.addContent(templateTag);
  }

  public static @NotNull <T extends PostfixTemplateExpressionCondition> Set<T> readExternalConditions(@NotNull Element template,
                                                                                                      @NotNull Function<? super Element, ? extends T> conditionFactory) {
    Element conditionsElement = template.getChild(CONDITIONS_TAG);
    if (conditionsElement != null) {
      Set<T> conditions = new LinkedHashSet<>();
      for (Element conditionElement : conditionsElement.getChildren(CONDITION_TAG)) {
        T fun = conditionFactory.fun(conditionElement);
        if (fun != null) {
          ContainerUtil.addIfNotNull(conditions, fun);
        }
      }

      return conditions;
    }

    return Collections.emptySet();
  }

  public static @Nullable TemplateImpl readExternalLiveTemplate(@NotNull Element template, @NotNull PostfixTemplateProvider provider) {
    Element templateChild = template.getChild(TemplateSettings.TEMPLATE);
    if (templateChild == null) return null;

    return TemplateSettings.readTemplateFromElement("", templateChild, provider.getClass().getClassLoader(),
                                                    LiveTemplateContextService.getInstance());
  }

  public static boolean readExternalTopmostAttribute(@NotNull Element template) {
    return Boolean.parseBoolean(template.getAttributeValue(TOPMOST_ATTR));
  }
}
