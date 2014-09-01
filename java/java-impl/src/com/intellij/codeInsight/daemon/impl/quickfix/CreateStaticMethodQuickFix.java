package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateStaticMethodQuickFix implements LocalQuickFix {
  @NotNull
  private final PsiClass targetClass;
  @NotNull
  private final String methodName;
  @NotNull
  private final List<String> types;


  public CreateStaticMethodQuickFix(@NotNull PsiClass aClass,
                                    @NotNull String name,
                                    @NotNull List<String> types) {
    targetClass = aClass;
    methodName = name;
    this.types = types;
  }


  @NotNull
  @Override
  public String getName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.method.from.usage.family");
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    boolean java8Interface = false;
    if (targetClass.isInterface()) {
      if (PsiUtil.isLanguageLevel8OrHigher(targetClass)) {
        java8Interface = true;
      } else {
        return;
      }
    }

    PsiMethod method = CreateMethodFromUsageFix.createMethod(targetClass, null, null, methodName);
    if (method == null) {
      return;
    }

    if (!java8Interface) {
      PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
    }
    PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);

    List<Pair<PsiExpression,PsiType>> args = ContainerUtil.map(types, new Function<String, Pair<PsiExpression, PsiType>>() {
      @Override
      public Pair<PsiExpression, PsiType> fun(String s) {
        return new Pair<PsiExpression, PsiType>(null, PsiType.getTypeByName(s, project, GlobalSearchScope.allScope(project)));
      }
    });
    CreateMethodFromUsageFix.doCreate(targetClass, method, false,
                                      args,
                                      PsiSubstitutor.UNKNOWN,
                                      ExpectedTypeInfo.EMPTY_ARRAY,
                                      null);
  }
}
