package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.GenericsHighlightUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;

import java.util.HashMap;

public class VariableParameterizedTypeFix {
  public static void registerIntentions(HighlightInfo highlightInfo, PsiVariable variable, PsiReferenceParameterList parameterList) {
    PsiType type = variable.getType();
    if (!(type instanceof PsiClassType)) return;

    String shortName = ((PsiClassType)type).getClassName();
    PsiManager manager = parameterList.getManager();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
    PsiShortNamesCache shortNamesCache = facade.getShortNamesCache();
    PsiClass[] classes = shortNamesCache.getClassesByName(shortName, GlobalSearchScope.allScope(manager.getProject()));
    PsiElementFactory factory = facade.getElementFactory();
    for (PsiClass aClass : classes) {
      if (GenericsHighlightUtil.checkReferenceTypeArgumentList(aClass, parameterList, PsiSubstitutor.EMPTY, false) == null) {
        PsiType[] actualTypeParameters = parameterList.getTypeArguments();
        PsiTypeParameter[] classTypeParameters = aClass.getTypeParameters();
        HashMap<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
        for (int j = 0; j < classTypeParameters.length; j++) {
          PsiTypeParameter classTypeParameter = classTypeParameters[j];
          PsiType actualTypeParameter = actualTypeParameters[j];
          map.put(classTypeParameter, actualTypeParameter);
        }
        PsiSubstitutor substitutor = factory.createSubstitutor(map);
        PsiType suggestedType = factory.createType(aClass, substitutor);
        QuickFixAction.registerQuickFixAction(highlightInfo, new VariableTypeFix(variable, suggestedType));
        QuickFixAction.registerQuickFixAction(highlightInfo, new TypeMigrationFix(variable, suggestedType));
      }
    }
  }
}
