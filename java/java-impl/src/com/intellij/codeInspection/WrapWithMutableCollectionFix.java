// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WrapWithMutableCollectionFix extends PsiUpdateModCommandQuickFix {
  private final String myVariableName;
  private final String myCollectionName;

  public WrapWithMutableCollectionFix(String variableName, String collectionName) {
    myVariableName = variableName;
    myCollectionName = collectionName;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getName() {
    return JavaBundle.message("quickfix.text.wrap.0.with.1", myVariableName, StringUtil.getShortName(myCollectionName));
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.family.wrap.with.mutable.collection");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiLocalVariable variable = getVariable(element);
    if (variable == null) return;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return;
    PsiClassType type = ObjectUtils.tryCast(variable.getType(), PsiClassType.class);
    if (type == null) return;
    String typeParameters = "";
    if (myCollectionName.equals(CommonClassNames.JAVA_UTIL_HASH_MAP)) {
      PsiType keyParameter = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 0, false);
      PsiType valueParameter = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_UTIL_MAP, 1, false);
      if (keyParameter != null && valueParameter != null) {
        typeParameters = "<"+keyParameter.getCanonicalText()+","+valueParameter.getCanonicalText()+">";
      }
    } else {
      PsiType elementParameter = PsiUtil.substituteTypeParameter(type, CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
      if (elementParameter != null) {
        typeParameters = "<" + elementParameter.getCanonicalText() + ">";
      }
    }
    CommentTracker ct = new CommentTracker();
    PsiElement replacement =
      ct.replaceAndRestoreComments(initializer, "new " + myCollectionName + typeParameters + "(" + ct.text(initializer) + ")");
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(replacement);
    updater.highlight(replacement);
  }

  @Nullable
  public static WrapWithMutableCollectionFix createFix(@NotNull PsiElement anchor) {
    PsiLocalVariable variable = getVariable(anchor);
    if (variable == null) return null;
    PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    String wrapper = getWrapperByType(variable.getType());
    if (wrapper == null) return null;
    PsiElement block = PsiUtil.getVariableCodeBlock(variable, null);
    if (block == null) return null;
    if (!HighlightControlFlowUtil.isEffectivelyFinal(variable, block, null)) return null;
    return new WrapWithMutableCollectionFix(variable.getName(), wrapper);
  }

  @Nullable
  private static PsiLocalVariable getVariable(@NotNull PsiElement anchor) {
    if (anchor.getParent() instanceof PsiReferenceExpression && anchor.getParent().getParent() instanceof PsiCallExpression) {
      anchor = ((PsiReferenceExpression)anchor.getParent()).getQualifierExpression();
    }
    if (!(anchor instanceof PsiExpression)) return null;
    return ExpressionUtils.resolveLocalVariable((PsiExpression)anchor);
  }

  @Contract("null -> null")
  @Nullable
  private static String getWrapperByType(PsiType type) {
    if(!(type instanceof PsiClassType)) return null;
    PsiClass aClass = ((PsiClassType)type).resolve();
    if (aClass == null) return null;
    String name = aClass.getQualifiedName();
    if (name == null) return null;
    return switch (name) {
      case CommonClassNames.JAVA_LANG_ITERABLE, CommonClassNames.JAVA_UTIL_COLLECTION, CommonClassNames.JAVA_UTIL_LIST ->
        CommonClassNames.JAVA_UTIL_ARRAY_LIST;
      case CommonClassNames.JAVA_UTIL_SET -> CommonClassNames.JAVA_UTIL_HASH_SET;
      case CommonClassNames.JAVA_UTIL_MAP -> CommonClassNames.JAVA_UTIL_HASH_MAP;
      default -> null;
    };
  }
}
