package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

/**
 * @author ven
 */
public abstract class ExpectedTypesProvider {
  public static ExpectedTypesProvider getInstance(Project project) {
    return project.getComponent(ExpectedTypesProvider.class);
  }

  public abstract ExpectedTypeInfo createInfo(PsiType type, int kind, PsiType defaultType, int tailType);

  public abstract void registerAdditionalProvider(AdditionalExpectedTypesProvider provider);

  public abstract ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr, boolean forCompletion);

  public abstract ExpectedTypeInfo[] getExpectedTypes(PsiExpression expr,
                                                    boolean forCompletion,
                                                    ExpectedClassProvider classProvider);

  public abstract PsiType[] processExpectedTypes(ExpectedTypeInfo[] infos,
                                          PsiTypeVisitor<PsiType> visitor, Project project);

  /**
   * Finds fields and methods of specified name whenever corresponding reference has been encountered.
   * By default searhes in the global scope (see ourGlobalScopeClassProvider), but caller can provide its own algorithm e.g. to narrow search scope
   */
  public interface ExpectedClassProvider {
    PsiField[] findDeclaredFields(final PsiManager manager, String name);

    PsiMethod[] findDeclaredMethods(final PsiManager manager, String name);
  }

}
