package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class IterableComponentTypeMacro implements Macro {
  public String getName() {
    return "iterableComponentType";
  }

  public String getDescription() {
    return CodeInsightBundle.message("macro.iterable.component.type");
  }

  public String getDefaultValue() {
    return "a";
  }

  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;
    PsiType type = expr.getType();
    if (type instanceof PsiArrayType) {
      return new PsiTypeResult(((PsiArrayType)type).getComponentType(), project);
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      PsiClass aClass = resolveResult.getElement();

      if (aClass != null) {
        PsiClass iterableClass = JavaPsiFacade.getInstance(project).findClass("java.lang.Iterable", aClass.getResolveScope());
        if (iterableClass != null) {
          PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(iterableClass, aClass, resolveResult.getSubstitutor());
          if (substitutor != null) {
            PsiType parameterType = substitutor.substitute(iterableClass.getTypeParameters()[0]);
            if (parameterType instanceof PsiCapturedWildcardType) {
              parameterType = ((PsiCapturedWildcardType)parameterType).getWildcard();
            }
            if (parameterType != null) {
              if (parameterType instanceof PsiWildcardType) {
                if (((PsiWildcardType)parameterType).isExtends()) {
                  return new PsiTypeResult(((PsiWildcardType)parameterType).getBound(), project);
                }
                else return null;
              }
              return new PsiTypeResult(parameterType, project);
            }
          }
        }
      }
    }

    return null;
  }

  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  public LookupItem[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }
}
