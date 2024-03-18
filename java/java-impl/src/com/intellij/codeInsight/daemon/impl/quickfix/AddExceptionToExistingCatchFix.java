// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class AddExceptionToExistingCatchFix extends PsiBasedModCommandAction<PsiElement> {
  public AddExceptionToExistingCatchFix(PsiElement errorElement) {
    super(errorElement);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext ctx, @NotNull PsiElement element) {
    Context context = Context.from(element);
    if (context == null) return ModCommand.nop();

    List<? extends PsiClassType> unhandledExceptions = context.myExceptions;
    List<? extends PsiCatchSection> catches = context.myCatches;

    List<@NotNull ModCommandAction> actions = ContainerUtil.map(
      catches, c -> ModCommand.psiUpdateStep(c,
                                             Objects.requireNonNull(c.getCatchType()).getPresentableText(),
                                             (section, updater) -> addTypeToCatch(unhandledExceptions, section, ctx.project()),
                                              section -> Objects.requireNonNull(section.getParameter()).getTextRange()));
    return ModCommand.chooseAction(QuickFixBundle.message("add.exception.to.existing.catch.chooser.title"), actions);
  }

  private static List<PsiCatchSection> findSuitableSections(List<? extends PsiCatchSection> sections, @NotNull List<? extends PsiClassType> exceptionTypes, boolean isJava7OrHigher) {
    List<PsiCatchSection> finalSections = new ArrayList<>();
    for (PsiCatchSection section : ContainerUtil.reverse(sections)) {
      finalSections.add(section);

      PsiType sectionType = section.getCatchType();
      if (sectionType == null) continue;
      for (PsiType exceptionType : exceptionTypes) {
        if (exceptionType.isAssignableFrom(sectionType)) {
          return finalSections;
          // adding type to any upper leads to compilation error
        }
      }
    }
    if (!isJava7OrHigher) {
      // if we get to this point, this means, that we can't generify any catch clause, so we can't suggest a fix
      return Collections.emptyList();
    }
    return finalSections;
  }

  private static void addTypeToCatch(@NotNull List<? extends PsiClassType> exceptionsToAdd, 
                                     @NotNull PsiCatchSection catchSection, 
                                     Project project) {
    if (!catchSection.isValid() || !ContainerUtil.and(exceptionsToAdd, type -> type.isValid())) return;
    PsiParameter parameter = catchSection.getParameter();
    if (parameter == null) return;
    PsiTypeElement typeElement = parameter.getTypeElement();
    if (typeElement == null) return;
    PsiType parameterType = parameter.getType();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String flattenText = getTypeText(exceptionsToAdd, parameterType);
    PsiElement newTypeElement = typeElement.replace(factory.createTypeElementFromText(flattenText, parameter));
    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(newTypeElement));
  }

  private static String getTypeText(@NotNull List<? extends PsiClassType> exceptionsToAdd,
                                    PsiType parameterType) {
    ArrayList<PsiType> types = new ArrayList<>();
    types.add(parameterType);
    types.addAll(exceptionsToAdd);
    return PsiDisjunctionType.flattenAndRemoveDuplicates(types)
      .stream()
      .map(type -> type.getCanonicalText())
      .collect(Collectors.joining(" | "));
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext ctx, @NotNull PsiElement element) {
    Context context = Context.from(element);
    if (context == null) return null;
    return Presentation.of(context.getMessage());
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.exception.to.existing.catch.family");
  }

  private static final class Context {
    private final List<? extends PsiCatchSection> myCatches;
    private final List<? extends PsiClassType> myExceptions;


    private Context(List<? extends PsiCatchSection> catches, List<? extends PsiClassType> exceptions) {
      myCatches = catches;
      myExceptions = exceptions;
    }

    @Nullable
    static Context from(@NotNull PsiElement element) {
      if (!element.isValid() || element instanceof PsiMethodReferenceExpression) return null;
      boolean isJava7OrHigher = PsiUtil.isLanguageLevel7OrHigher(element);
      List<PsiClassType> unhandledExceptions = new ArrayList<>(ExceptionUtil.getOwnUnhandledExceptions(element));
      if (unhandledExceptions.isEmpty()) return null;
      List<PsiTryStatement> tryStatements = getTryStatements(element);
      List<PsiCatchSection> sections =
        tryStatements.stream()
          .flatMap(stmt -> findSuitableSections(Arrays.asList(stmt.getCatchSections()), unhandledExceptions, isJava7OrHigher).stream())
          .filter(catchSection -> {
            PsiParameter parameter = catchSection.getParameter();
            if (parameter == null) return false;
            return parameter.getTypeElement() != null;
          })
          .collect(Collectors.toList());
      if (sections.isEmpty()) return null;
      return new Context(sections, unhandledExceptions);
    }

    @NotNull
    private static List<PsiTryStatement> getTryStatements(@NotNull PsiElement element) {
      PsiElement current = element;
      PsiElement parent = element.getParent();
      List<PsiTryStatement> parents = new SmartList<>();
      while (parent != null) {
        if (parent instanceof PsiLambdaExpression || parent instanceof PsiMember || parent instanceof PsiFile) break;
        if (parent instanceof PsiTryStatement tryStatement) {
          if (tryStatement.getFinallyBlock() != current && !(current instanceof PsiCatchSection)) {
            parents.add((PsiTryStatement)parent);
          }
        }
        current = parent;
        parent = parent.getParent();
      }
      return parents;
    }

    private @IntentionName String getMessage() {
      if (myCatches.size() == 1 && myExceptions.size() == 1) {
        PsiClassType exceptionType = myExceptions.get(0);
        PsiCatchSection catchSection = myCatches.get(0);
        PsiParameter parameter = catchSection.getParameter();
        assert parameter != null;
        PsiType catchType = parameter.getType();
        if (replacementNeeded(exceptionType, catchType)) {
          return QuickFixBundle.message("add.exception.to.existing.catch.replacement", catchType.getPresentableText(), exceptionType.getPresentableText());
        }
        else {
          return QuickFixBundle.message("add.exception.to.existing.catch.no.replacement", catchType.getPresentableText(), exceptionType.getPresentableText());
        }
      }
      return QuickFixBundle.message("add.exception.to.existing.catch.generic");
    }
  }

  private static boolean replacementNeeded(@NotNull PsiClassType newException, @NotNull PsiType catchType) {
    if (catchType instanceof PsiDisjunctionType disjunction) {
      for (PsiType type : disjunction.getDisjunctions()) {
        if (newException.isAssignableFrom(type)) {
          return true;
        }
      }
      return false;
    }
    return newException.isAssignableFrom(catchType);
  }
}
