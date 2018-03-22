// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AddExceptionToExistingCatchFix extends PsiElementBaseIntentionAction {
  private final PsiElement myErrorElement;

  public AddExceptionToExistingCatchFix(PsiElement errorElement) {myErrorElement = errorElement;}

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    if (tryStatement == null) return;
    PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    if (catchSections.length == 0) return;
    List<PsiClassType> unhandledExceptions = new ArrayList<>(ExceptionUtil.getOwnUnhandledExceptions(myErrorElement));
    if (unhandledExceptions.size() != 1) return;
    List<String> catchTexts = getAvailableCatchSections(catchSections)
      .map(s -> s.getCatchType())
      .filter(Objects::nonNull)
      .map(type -> type.getPresentableText())
      .collect(Collectors.toList());

    Application application = ApplicationManager.getApplication();
    if (catchSections.length == 1 || application.isUnitTestMode()) {
      PsiCatchSection selectedSection = catchSections[0];
      addTypeToCatch(unhandledExceptions.get(0), selectedSection);
    }
    else {
      JBList<String> list = new JBList<>(catchTexts);
      JBPopupFactory.getInstance().createListPopupBuilder(list)
                    .setTitle("Select catch block")
                    .setMovable(false)
                    .setResizable(false)
                    .setRequestFocus(true)
                    .setItemChoosenCallback(() -> {
                      int selectedIndex = list.getSelectedIndex();
                      PsiCatchSection selectedSection = catchSections[selectedIndex];
                      addTypeToCatch(unhandledExceptions.get(0), selectedSection);
                    })
                    .createPopup()
                    .showInBestPositionFor(editor);
    }
  }

  @NotNull
  private static Stream<PsiCatchSection> getAvailableCatchSections(PsiCatchSection[] catchSections) {
    return Arrays.stream(catchSections)
                 .filter(catchSection -> {
                   PsiParameter parameter = catchSection.getParameter();
                   if (parameter == null) return false;
                   return parameter.getTypeElement() != null;
                 });
  }

  private static void addTypeToCatch(@NotNull PsiClassType exceptionToAdd, @NotNull PsiCatchSection catchSection) {
    WriteCommandAction.runWriteCommandAction(catchSection.getProject(), () -> {
      if (!catchSection.isValid() || !exceptionToAdd.isValid()) return;
      PsiParameter parameter = catchSection.getParameter();
      if (parameter == null) return;
      PsiTypeElement typeElement = parameter.getTypeElement();
      if (typeElement == null) return;
      PsiType parameterType = parameter.getType();
      boolean needReplace = exceptionToAdd.isAssignableFrom(parameterType);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(catchSection.getProject());
      String typeText = needReplace ? exceptionToAdd.getCanonicalText()
                                    : parameterType.getCanonicalText() + " | " + exceptionToAdd.getCanonicalText();
      typeElement.replace(factory.createTypeElementFromText(typeText, parameter));
    });
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
    if (tryStatement == null) return false;
    PsiCatchSection[] catchSections = tryStatement.getCatchSections();
    if (catchSections.length == 0) return false;
    if (notFinishedCatches(catchSections)) return false;
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class, PsiThrowStatement.class);
    if (parent == null) return false;
    List<PsiClassType> unhandledExceptions = new ArrayList<>(ExceptionUtil.getOwnUnhandledExceptions(myErrorElement));
    return unhandledExceptions.size() == 1;
  }

  private static boolean notFinishedCatches(PsiCatchSection[] catchSections) {
    return getAvailableCatchSections(catchSections)
      .map(catchSection -> catchSection.getParameter())
      .noneMatch(parameter -> parameter != null && parameter.getTypeElement() != null);
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
}
