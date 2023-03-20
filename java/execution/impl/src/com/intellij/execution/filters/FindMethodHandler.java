// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.navigation.TargetPresentation;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.openapi.util.text.StringUtil.getShortName;

final class FindMethodHandler extends AnAction {

  private static final String LAMBDA_KEYWORD = "lambda$";
  private final PsiFile myPsiFile;
  private final String myClassName;
  @NlsSafe private final String myMethodName;
  private final boolean isLambda;
  private final ExceptionLineRefiner myRefiner;
  private final Editor myEditor;

  private FindMethodHandler(@NotNull PsiFile file,
                            @NotNull String className,
                            @NlsSafe @NotNull String methodName,
                            boolean isLambda,
                            @NotNull ExceptionLineRefiner refiner,
                            @NotNull Editor targetEditor) {

    super(null, JavaBundle.message("action.find.similar.stack.call.diverged", getShortName(className), methodName), null);
    this.myPsiFile = file;
    this.myClassName = className;
    this.myMethodName = methodName;
    this.isLambda = isLambda;
    this.myRefiner = refiner;
    this.myEditor = targetEditor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Document document = PsiDocumentManager.getInstance(myPsiFile.getProject()).getDocument(myPsiFile);
    if (document == null) {
      return;
    }
    //test only
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Collection<PsiElement> elements = collector(myPsiFile, myClassName, myMethodName, isLambda, myRefiner).get();
      if (elements.size() >= 1) {
        elements.stream()
          .max(Comparator.comparing(el -> el.getTextOffset()))
          .ifPresent(el -> ((Navigatable)el).navigate(true));
      }
      return;
    }
    boolean found = new PsiTargetNavigator<>(collector(myPsiFile, myClassName, myMethodName, isLambda, myRefiner))
      .presentationProvider(element -> {
        String text;
        String containerText;
        if (element instanceof PsiMethod psiMethod) {
          text = psiMethod.getName();
          containerText = getShortName(myClassName) + ": " + document.getLineNumber(element.getTextRange().getStartOffset());
        }
        else {
          PsiCallExpression expression = PsiTreeUtil.getParentOfType(element, PsiCallExpression.class);
          text = (expression != null ? expression.getText() : element.getText());
          containerText = getShortName(myClassName) + "." + myMethodName + "()" +
                          ": " + document.getLineNumber(element.getTextRange().getStartOffset());
        }
        return TargetPresentation
          .builder(text)
          .containerText(containerText)
          .presentation();
      })
      .elementsConsumer((elements, navigator) -> {
        if (elements.isEmpty()) {
          return;
        }
        PsiElement next = elements.iterator().next();
        String message;
        if (next instanceof PsiMethod) {
          message = JavaBundle.message("action.find.similar.stack.call.methods", getShortName(myClassName), myMethodName);
        }
        else {
          message = JavaBundle.message("action.find.similar.stack.call.similar.calls", getShortName(myClassName), myMethodName);
        }
        navigator.title(message);
        navigator.tabTitle(message);
      })
      .navigate(myEditor, null, element -> EditSourceUtil.navigateToPsiElement(element));
    if (!found) {
      RelativePoint popupLocation = JBPopupFactory.getInstance().guessBestPopupLocation(myEditor);
      String message =
        JavaBundle.message("action.find.similar.stack.call.methods.not.found", getShortName(myClassName), myMethodName);
      final JComponent label = HintUtil.createErrorLabel(message);
      label.setBorder(JBUI.Borders.empty(2, 7));
      JBPopupFactory.getInstance().createBalloonBuilder(label)
        .setFadeoutTime(4000)
        .setFillColor(HintUtil.getInformationColor())
        .createBalloon()
        .show(popupLocation, Balloon.Position.above);
    }
  }

  @NotNull
  private static Supplier<Collection<PsiElement>> collector(@NotNull PsiFile psiFile, @NotNull String className, @NotNull String methodName,
                                                            boolean isLambda, @NotNull ExceptionLineRefiner refiner) {
    return () -> {
      Set<PsiElement> result = new HashSet<>();
      List<PsiElement> startPoints = new ArrayList<>();
      JavaRecursiveElementVisitor fileVisitor = new JavaRecursiveElementVisitor() {

        @Override
        public void visitMethod(@NotNull PsiMethod method) {
          ProgressManager.checkCanceled();
          if (!isLambda && matchMethod(method, methodName, className)) {
            startPoints.add(method);
          }

          if (isLambda) {
            super.visitMethod(method);
          }
        }

        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
          ProgressManager.checkCanceled();
          if (isLambda) {
            PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if (method != null && matchMethod(method, methodName, className)) {
              startPoints.add(expression);
            }
          }
        }
      };
      psiFile.acceptChildren(fileVisitor);
      for (PsiElement method : startPoints) {
        PsiTreeUtil.processElements(method, new PsiElementProcessor<>() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            ProgressManager.checkCanceled();
            PsiElement matchedElement = refiner.matchElement(element);
            PsiMethod matchedElementMethod = PsiTreeUtil.getParentOfType(matchedElement, PsiMethod.class);
            if (matchedElementMethod != null &&
                matchMethod(matchedElementMethod, methodName, className) &&
                matchedElement instanceof Navigatable) {
              result.add(matchedElement);
            }
            return true;
          }
        });
      }
      if (result.isEmpty()) {
        result.addAll(startPoints);
      }
      return result;
    };
  }

  private static boolean matchMethod(@NotNull PsiElement element,
                                     @NotNull String methodName,
                                     @NotNull String className) {
    return element instanceof PsiMethod psiMethod &&
           (methodName.equals(psiMethod.getName()) || (methodName.equals("<init>") && psiMethod.isConstructor())) &&
           (psiMethod.getContainingClass() != null && className.equals(psiMethod.getContainingClass().getQualifiedName()));
  }

  static ExceptionLineParserImpl.@Nullable LinkInfo createLinkInfo(@Nullable PsiFile file,
                                                                   @Nullable String className,
                                                                   @Nullable String methodName,
                                                                   @Nullable ExceptionLineRefiner refiner,
                                                                   @Nullable Editor targetEditor) {
    //it can be ambiguous to find lambda or anonymous classes
    if (file == null || className == null || methodName == null || refiner == null || targetEditor == null) {
      return null;
    }
    if (!file.getLanguage().is(JavaLanguage.INSTANCE)) {
      return null;
    }

    int anonymousClassIndex = className.indexOf("$");
    if (anonymousClassIndex != -1) {
      String shortName = getShortName(className, '$');
      if (shortName.length() == 0) {
        return null;
      }
      if (Character.isDigit(shortName.charAt(0))) {
        return null;
      }
    }
    className = className.replaceAll("\\$", ".");
    boolean isLambda = false;
    if (methodName.contains("$")) {
      int lambdaIndex = methodName.indexOf(LAMBDA_KEYWORD);
      if (lambdaIndex != -1) {
        isLambda = true;
        methodName = methodName.substring(lambdaIndex + LAMBDA_KEYWORD.length());
        int nextDelimiter = methodName.indexOf("$");
        if (nextDelimiter != -1) {
          methodName = methodName.substring(0, nextDelimiter);
        }
      }
      else {
        return null;
      }
      if (methodName.isEmpty()) {
        return null;
      }
    }

    if (DumbService.isDumb(file.getProject())) {
      return null;
    }
    return new ExceptionLineParserImpl.LinkInfo(file, null,
                                                new FindMethodHandler(file, className, methodName, isLambda, refiner,
                                                                      targetEditor),
                                                finder -> {
                                                });
  }
}
