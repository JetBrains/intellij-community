package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class IterableVariableMacro extends VariableTypeMacroBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.template.macro.IterableVariableMacro");

  public String getName() {
    return "iterableVariable";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.iterable.variable");
  }

  @Nullable
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final List<PsiElement> result = new ArrayList<PsiElement>();


    Project project = context.getProject();
    final int offset = context.getStartOffset();
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    assert file != null;
    PsiElement place = file.findElementAt(offset);
    final PsiElementFactory elementFactory = PsiManager.getInstance(project).getElementFactory();
    final GlobalSearchScope scope = file.getResolveScope();

    PsiType iterableType = elementFactory.createTypeByFQClassName("java.lang.Iterable", scope);
    PsiType mapType = elementFactory.createTypeByFQClassName("java.util.Map", scope);

    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for (PsiVariable var : variables) {
      final PsiElement parent = var.getParent();
      if (parent instanceof PsiForeachStatement && parent == PsiTreeUtil.getParentOfType(place, PsiForeachStatement.class)) continue;

      PsiType type = var.getType();
      if (type instanceof PsiArrayType || iterableType.isAssignableFrom(type)) {
        result.add(var);
      }
      else if (mapType.isAssignableFrom(type)) {
        try {
          result.add(elementFactory.createExpressionFromText(var.getName() + ".keySet()", place));
          result.add(elementFactory.createExpressionFromText(var.getName() + ".values()", place));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    return result.toArray(new PsiElement[result.size()]);
  }
}
