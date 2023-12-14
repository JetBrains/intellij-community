// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.execution.ApplicationRunLineMarkerHider;
import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
public class ApplicationRunLineMarkerProvider extends RunLineMarkerContributor {
  private final static Comparator<PsiMethod> mainCandidateComparator = (o1, o2) -> {

    boolean isO1Static = o1.hasModifierProperty(PsiModifier.STATIC);
    boolean isO2Static = o2.hasModifierProperty(PsiModifier.STATIC);
    int o1Parameters = o1.getParameterList().getParametersCount();
    int o2Parameters = o2.getParameterList().getParametersCount();

    boolean is22PreviewOrLater = PsiUtil.getLanguageLevel(o1).isAtLeast(LanguageLevel.JDK_22_PREVIEW);

    if (is22PreviewOrLater) {
      return Integer.compare(o2Parameters, o1Parameters);
    }

    //only for java 21 preview
    if(isO1Static == isO2Static) {
      return Integer.compare(o2Parameters, o1Parameters);
    } else if(isO1Static) {
      return -1;
    } else {
      return 1;
    }
  };

  @Override
  public final @Nullable Info getInfo(@NotNull final PsiElement element) {
    if (Registry.is("ide.jvm.run.marker") ||
        !isIdentifier(element) ||
        ApplicationRunLineMarkerHider.shouldHideRunLineMarker(element)) {
      return null;
    }

    PsiElement parent = element.getParent();
    if (parent instanceof PsiClass aClass) {
      if (PsiMethodUtil.findMainInClass(aClass) == null) return null;
    }
    else if (parent instanceof PsiMethod method) {
      if (!"main".equals(method.getName()) || !PsiMethodUtil.isMainMethod(method)) return null;
      PsiClass containingClass = method.getContainingClass();
      if (!(containingClass instanceof PsiImplicitClass) && PsiTreeUtil.getParentOfType(containingClass, PsiImplicitClass.class) != null) return null;
      if (containingClass == null || PsiUtil.isLocalOrAnonymousClass(containingClass)) return null;
      PsiMethod[] constructors = containingClass.getConstructors();
      if (!method.hasModifierProperty(PsiModifier.STATIC) && constructors.length != 0 && !ContainerUtil.exists(constructors, method1 -> method1.getParameterList().isEmpty())) {
        return null;
      }
      if (containingClass.hasModifierProperty(PsiModifier.ABSTRACT) && !method.hasModifierProperty(PsiModifier.STATIC)) return null;
      Optional<PsiMethod> mainMethod =
        Arrays.stream(containingClass.getMethods())
          .filter(m -> "main".equals(m.getName()) && PsiMethodUtil.isMainMethod(m))
          .min(mainCandidateComparator);
      assert mainMethod.isPresent();
      PsiMethod mainCandidate = mainMethod.get();
      if (mainCandidate != method) return null;
    }
    else {
      return null;
    }
    if (JavaHighlightUtil.isJavaHashBangScript(element.getContainingFile())) {
      return null;
    }

    AnAction[] actions = ExecutorAction.getActions(Integer.MAX_VALUE);
    return new Info(AllIcons.RunConfigurations.TestState.Run, actions, new ActionsTooltipProvider(actions));
  }

  private record ActionsTooltipProvider(@NotNull AnAction @NotNull [] myActions) implements Function<PsiElement, String> {
    @Override
    public String apply(PsiElement element) {
      AnActionEvent event = createActionEvent(element);
      return Arrays.stream(myActions)
            .map(action -> getText(action, event))
            .filter(Objects::nonNull)
            .collect(Collectors.joining("\n"));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myActions);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ActionsTooltipProvider other && Arrays.equals(myActions, other.myActions);
    }
  }

  protected boolean isIdentifier(@NotNull PsiElement e) {
    return e instanceof PsiIdentifier;
  }
}
