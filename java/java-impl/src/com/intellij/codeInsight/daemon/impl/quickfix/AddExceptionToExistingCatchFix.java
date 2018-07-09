// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.IntroduceTargetChooser;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AddExceptionToExistingCatchFix extends PsiElementBaseIntentionAction {
  private final PsiElement myErrorElement;

  public AddExceptionToExistingCatchFix(PsiElement errorElement) {myErrorElement = errorElement;}

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(myErrorElement)) return;
    Context context = Context.from(myErrorElement);
    if (context == null) return;

    List<PsiCatchSection> catchSections = context.myCatches;
    List<PsiClassType> unhandledExceptions = context.myExceptions;
    List<PsiCatchSection> catches = catchSections.stream()
                                         .filter(s -> s.getCatchType() != null && s.getParameter() != null)
                                         .collect(Collectors.toList());

    setText(context.getMessage());

    Application application = ApplicationManager.getApplication();

    if (catchSections.size() == 1 || application.isUnitTestMode()) {
      PsiCatchSection selectedSection = catchSections.get(0);
      addTypeToCatch(unhandledExceptions, selectedSection);
    }
    else {
      IntroduceTargetChooser.showChooser(
        editor,
        catches,
        new Pass<PsiCatchSection>() {
          @Override
          public void pass(PsiCatchSection section) {
            addTypeToCatch(unhandledExceptions, section);
          }
        },
        section -> Objects.requireNonNull(section.getCatchType()).getPresentableText(),
        QuickFixBundle.message("add.exception.to.existing.catch.chooser.title"),
        catchSection -> Objects.requireNonNull(((PsiCatchSection)catchSection).getParameter()).getTextRange()
      );
    }
  }

  private static void addTypeToCatch(@NotNull List<PsiClassType> exceptionsToAdd, @NotNull PsiCatchSection catchSection) {
    Project project = catchSection.getProject();
    WriteCommandAction.runWriteCommandAction(project, () -> {
      if (!catchSection.isValid() || !exceptionsToAdd.stream().allMatch(type -> type.isValid())) return;
      PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) return;
      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) return;
      PsiType parameterType = parameter.getType();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      String flattenText = getTypeText(exceptionsToAdd, parameter, parameterType, factory);
      PsiElement newTypeElement = typeElement.replace(factory.createTypeElementFromText(flattenText, parameter));
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(newTypeElement));
    });
  }

  private static String getTypeText(@NotNull List<PsiClassType> exceptionsToAdd,
                                    PsiParameter parameter,
                                    PsiType parameterType,
                                    PsiElementFactory factory) {
    String typeText = parameterType.getCanonicalText() + " | " + exceptionsToAdd.stream()
                                                                                .map(type -> type.getCanonicalText())
                                                                                .collect(Collectors.joining(" | "));
    PsiTypeElement element = factory.createTypeElementFromText(typeText, parameter);
    List<PsiType> flatten = PsiDisjunctionType.flattenAndRemoveDuplicates(((PsiDisjunctionType)element.getType()).getDisjunctions());
    return flatten.stream()
                  .map(type -> type.getCanonicalText())
                  .collect(Collectors.joining(" | "));
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    return Context.from(myErrorElement) != null;
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.exception.to.existing.catch.family");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  private static class Context {
    private final List<PsiCatchSection> myCatches;
    private final List<PsiClassType> myExceptions;


    private Context(List<PsiCatchSection> catches, List<PsiClassType> exceptions) {
      myCatches = catches;
      myExceptions = exceptions;
    }

    @Nullable
    static Context from(@NotNull PsiElement element) {
      if (!element.isValid() || !PsiUtil.isLanguageLevel7OrHigher(element)) return null;
      List<PsiClassType> unhandledExceptions = new ArrayList<>(ExceptionUtil.getOwnUnhandledExceptions(element));
      if (unhandledExceptions.isEmpty()) return null;
      List<PsiTryStatement> tryStatements = getTryStatements(element);
      List<PsiCatchSection> sections =
        tryStatements.stream()
                     .flatMap(stmt -> Arrays.stream(stmt.getCatchSections()))
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
        if (parent instanceof PsiTryStatement) {
          PsiTryStatement tryStatement = (PsiTryStatement)parent;
          if (tryStatement.getFinallyBlock() != current && !(current instanceof PsiCatchSection)) {
            parents.add((PsiTryStatement)parent);
          }
        }
        current = parent;
        parent = parent.getParent();
      }
      return parents;
    }

    private String getMessage() {
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
    if (catchType instanceof PsiDisjunctionType) {
      PsiDisjunctionType disjunction = (PsiDisjunctionType)catchType;
      for (PsiType type : disjunction.getDisjunctions()) {
        if (type.isAssignableFrom(newException)) {
          return true;
        }
      }
      return false;
    }
    return catchType.isAssignableFrom(newException);
  }
}
