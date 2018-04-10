// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.settings.PostfixTemplateStorage;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixChangedBuiltinTemplate;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public abstract class PostfixTemplatesUtils {
  private PostfixTemplatesUtils() {
  }

  /**
   * Returns all templates registered in the provider, including the edited templates and builtin templates in their current state
   */
  @NotNull
  public static Set<PostfixTemplate> getAvailableTemplates(@NotNull PostfixTemplateProvider provider) {
    Set<PostfixTemplate> result = ContainerUtil.newHashSet(provider.getTemplates());
    for (PostfixTemplate template : PostfixTemplateStorage.getInstance().getTemplates(provider)) {
      if (template instanceof PostfixChangedBuiltinTemplate) {
        result.remove(((PostfixChangedBuiltinTemplate)template).getBuiltinTemplate());
      }
      result.add(template);
    }
    return result;
  }

  @Nullable
  public static TextRange surround(@NotNull Surrounder surrounder,
                                   @NotNull Editor editor,
                                   @NotNull PsiElement expr) {
    Project project = expr.getProject();
    PsiElement[] elements = {expr};
    if (surrounder.isApplicable(elements)) {
      return surrounder.surroundElements(project, editor, elements);
    }
    else {
      showErrorHint(project, editor);
    }
    return null;
  }

  public static void showErrorHint(@NotNull Project project, @NotNull Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, "Can't expand postfix template", "Can't expand postfix template", "");
  }

  @NotNull
  public static String generateTemplateId(@NotNull String templateKey, @NotNull PostfixTemplateProvider provider) {
    Set<String> usedIds = new HashSet<>();
    for (PostfixTemplate builtinTemplate : provider.getTemplates()) {
      usedIds.add(builtinTemplate.getId());
    }
    for (PostfixTemplate template : PostfixTemplateStorage.getInstance().getTemplates(provider)) {
      usedIds.add(template.getId());
    }
    return UniqueNameGenerator.generateUniqueName(templateKey + "@userDefined", usedIds);
  }
}
