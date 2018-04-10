// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.intention.impl.TypeExpression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class TryWithResourcesPostfixTemplate extends PostfixTemplate {
  protected TryWithResourcesPostfixTemplate() {
    super("twr", "try(Type f = new Type()) catch (Exception e)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement element, @NotNull Document copyDocument, int newOffset) {
    if (!PsiUtil.isLanguageLevel7OrHigher(element)) return false;

    PsiExpression initializer = JavaPostfixTemplatesUtils.getTopmostExpression(element);

    if (initializer == null) return false;

    final PsiType type = initializer.getType();
    if (!(type instanceof PsiClassType)) return false;
    final PsiClass aClass = ((PsiClassType)type).resolve();
    Project project = element.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass autoCloseable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, ProjectScope.getLibrariesScope(project));
    if (!InheritanceUtil.isInheritorOrSelf(aClass, autoCloseable, true)) return false;

    return true;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = JavaPostfixTemplatesUtils.getTopmostExpression(context);
    assert expression != null;

    Project project = context.getProject();

    editor.getDocument().deleteString(expression.getTextRange().getStartOffset(), expression.getTextRange().getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    Template template = manager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("try (");
    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());

    template.addVariable("type", new TypeExpression(project, new PsiType[]{expression.getType()}), false);
    template.addTextSegment(" ");
    template.addVariable("name", name, name, true);
    template.addTextSegment(" = ");
    template.addVariable("variable", new TextExpression(expression.getText()), false);
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    Collection<PsiClassType> unhandled = getUnhandled(expression);
    for (PsiClassType exception : unhandled) {
      MacroCallNode variable = new MacroCallNode(new SuggestVariableNameMacro());
      template.addTextSegment("catch(");
      template.addVariable("type " + exception.getClassName(), new TypeExpression(project, new PsiType[]{exception}), false);
      template.addTextSegment(" ");
      template.addVariable("name " + exception.getClassName(), variable, variable, false);
      template.addTextSegment(") {}");
    }

    manager.startTemplate(editor, template);
  }

  @NotNull
  private static Collection<PsiClassType> getUnhandled(@NotNull PsiExpression expression) {
    assert expression.getType() != null;
    return ExceptionUtil.getUnhandledCloserExceptions(expression, null, expression.getType());
  }
}
