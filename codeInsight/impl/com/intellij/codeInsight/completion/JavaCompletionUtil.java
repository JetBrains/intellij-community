package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.Set;

public class JavaCompletionUtil {
  public static void completeLocalVariableName(Set<LookupItem> set, CompletionContext context, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(var.getProject());
    final VariableKind variableKind = codeStyleManager.getVariableKind(var);

    String propertyName = null;
    if (variableKind == VariableKind.PARAMETER) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
      propertyName = PropertyUtil.getPropertyName(method);
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, propertyName, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, context.getPrefix()), suggestedNameInfo);

    if (set.isEmpty()) {
      suggestedNameInfo = new SuggestedNameInfo(CompletionUtil.getOverlappedNameVersions(context.getPrefix(), suggestedNames, "")) {
        public void nameChoosen(String name) {
        }
      };

      CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, context.getPrefix()), suggestedNameInfo);
    }
    PsiElement parent = PsiTreeUtil.getParentOfType(var, PsiCodeBlock.class);
    if(parent == null) parent = PsiTreeUtil.getParentOfType(var, PsiMethod.class);
    CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, CompletionUtil.getUnresolvedReferences(parent, false), context.getPrefix()), suggestedNameInfo);
    final String[] nameSuggestions =
      StatisticsManager.getInstance().getNameSuggestions(var.getType(), StatisticsManager.getContext(var), context.getPrefix());
    CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, nameSuggestions, context.getPrefix()), suggestedNameInfo);
  }

  public static void completeFieldName(Set<LookupItem> set, CompletionContext context, PsiVariable var){
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.variable.name");

    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(context.project);
    final VariableKind variableKind = JavaCodeStyleManager.getInstance(var.getProject()).getVariableKind(var);
    final String prefix = context.getPrefix();

    if (var.getType() == PsiType.VOID ||
        prefix.startsWith(CompletionUtil.IS_PREFIX) ||
        prefix.startsWith(CompletionUtil.GET_PREFIX) ||
        prefix.startsWith(CompletionUtil.SET_PREFIX)) {
      CompletionUtil.completeVariableNameForRefactoring(var.getProject(), set, prefix, var.getType(), variableKind);
      return;
    }

    SuggestedNameInfo suggestedNameInfo = codeStyleManager.suggestVariableName(variableKind, null, null, var.getType());
    final String[] suggestedNames = suggestedNameInfo.names;
    CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNames, prefix), suggestedNameInfo);

    if (set.isEmpty()) {
      // use suggested names as suffixes
      final String requiredSuffix = codeStyleManager.getSuffixByVariableKind(variableKind);
      if(variableKind != VariableKind.STATIC_FINAL_FIELD){
        for (int i = 0; i < suggestedNames.length; i++)
          suggestedNames[i] = codeStyleManager.variableNameToPropertyName(suggestedNames[i], variableKind);
      }


        suggestedNameInfo = new SuggestedNameInfo(CompletionUtil.getOverlappedNameVersions(prefix, suggestedNames, requiredSuffix)) {
        public void nameChoosen(String name) {
        }
      };

      CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, suggestedNameInfo.names, prefix), suggestedNameInfo);
    }

    CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, StatisticsManager.getInstance().getNameSuggestions(var.getType(), StatisticsManager.getContext(var), prefix), prefix), suggestedNameInfo);
    CompletionUtil.tunePreferencePolicy(LookupItemUtil.addLookupItems(set, CompletionUtil.getUnresolvedReferences(var.getParent(), false), context.getPrefix()), suggestedNameInfo);
  }

  public static void completeMethodName(Set<LookupItem> set, CompletionContext context, PsiElement element){
    if(element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        final PsiClass containingClass = method.getContainingClass();
        final String name = containingClass.getName();
        if (StringUtil.isNotEmpty(name)) {
          LookupItemUtil.addLookupItem(set, name, context.getPrefix());
        }
        return;
      }
    }

    LookupItemUtil.addLookupItems(set, CompletionUtil.getUnresolvedReferences(element.getParent(), true), context.getPrefix());
    if(!((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PRIVATE)){
      LookupItemUtil.addLookupItems(set, CompletionUtil.getOverides((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)),
                                    context.getPrefix());
      LookupItemUtil.addLookupItems(set, CompletionUtil.getImplements((PsiClass)element.getParent(), PsiUtil.getTypeByPsiElement(element)),
                                    context.getPrefix());
    }
    LookupItemUtil.addLookupItems(set, CompletionUtil.getPropertiesHandlersNames(
      (PsiClass)element.getParent(),
      ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC),
      PsiUtil.getTypeByPsiElement(element), element), context.getPrefix());
  }

}