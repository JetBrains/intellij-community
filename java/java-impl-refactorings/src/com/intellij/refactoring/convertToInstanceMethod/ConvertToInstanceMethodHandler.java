// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ConvertToInstanceMethodHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(ConvertToInstanceMethodHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }

    if (element == null) return;
    if (element instanceof PsiIdentifier) element = element.getParent();

    if(!(element instanceof PsiMethod)) {
      String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("error.wrong.caret.position.method"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.CONVERT_TO_INSTANCE_METHOD);
      return;
    }
    if(LOG.isDebugEnabled()) {
      LOG.debug("MakeMethodStaticHandler invoked");
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1 || !(elements[0] instanceof PsiMethod method)) return;
    try {
      new ConvertToInstanceMethodDialog(method, calculatePossibleInstanceQualifiers(method)).show();
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) throw e;
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(e.getMessage()),
                                          getRefactoringName(), HelpID.CONVERT_TO_INSTANCE_METHOD);
    }
  }

  private static Object @NotNull [] calculatePossibleInstanceQualifiers(@NotNull PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      throw new CommonRefactoringUtil.RefactoringErrorHintException(
        JavaRefactoringBundle.message("convertToInstanceMethod.method.is.not.static", method.getName()));
    }
    List<Object> qualifiers = new ArrayList<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    for (final PsiParameter parameter : parameters) {
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType classType) {
        classTypesFound = true;
        final PsiClass psiClass = classType.resolve();
        if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
          resolvableClassesFound = true;
          if (method.getManager().isInProject(psiClass)) {
            qualifiers.add(parameter);
          }
        }
      }
    }
    PsiClass containingClass = method.getContainingClass();
    boolean canHaveUsableConstructor = containingClass != null && 
                                       containingClass.getQualifiedName() != null && 
                                       !containingClass.isEnum() &&
                                       !PsiUtil.isInnerClass(containingClass) && 
                                       !(containingClass instanceof PsiImplicitClass);
    if (canHaveUsableConstructor) {
      PsiMethod[] constructors = containingClass.getConstructors();
      boolean noArgConstructor =
        constructors.length == 0 || ContainerUtil.exists(constructors, constructor -> constructor.getParameterList().isEmpty());
      if (noArgConstructor) {
        qualifiers.add("this / new " + containingClass.getName() + "()");
      }
    }
    if (!qualifiers.isEmpty()) {
      return qualifiers.toArray();
    }
    
    String message;
    if (!classTypesFound) {
      message = JavaRefactoringBundle.message("convertToInstanceMethod.no.parameters.with.reference.type");
    }
    else if (!resolvableClassesFound) {
      message = JavaRefactoringBundle.message("convertToInstanceMethod.all.reference.type.parameters.have.unknown.types");
    }
    else {
      message = JavaRefactoringBundle.message("convertToInstanceMethod.all.reference.type.parameters.are.not.in.project");
    }
    if (canHaveUsableConstructor) {
      message += " " + JavaRefactoringBundle.message("convertToInstanceMethod.no.default.ctor");
    }
    throw new CommonRefactoringUtil.RefactoringErrorHintException(message);
  }

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiElement caretElement = BaseRefactoringAction.getElementAtCaret(editor, file);
    return MethodUtils.getJavaMethodFromHeader(caretElement) != null;
  }

  static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("convert.to.instance.method.title");
  }
}
