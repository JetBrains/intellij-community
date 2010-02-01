/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.inline;

import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author yole
 */
public class InlineParameterExpressionProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineParameterExpressionProcessor");

  private final PsiCallExpression myMethodCall;
  private final PsiMethod myMethod;
  private final PsiParameter myParameter;
  private final PsiExpression myInitializer;
  private final Editor myEditor;
  private final boolean mySameClass;
  private final PsiMethod myCallingMethod;
  private Map<PsiVariable, PsiElement> myLocalReplacements;

  public InlineParameterExpressionProcessor(final PsiCallExpression methodCall,
                                            final PsiMethod method,
                                            final PsiParameter parameter,
                                            final PsiExpression initializer, Editor editor) {
    myMethodCall = methodCall;
    myMethod = method;
    myParameter = parameter;
    myInitializer = initializer;
    myEditor = editor;

    PsiClass callingClass = PsiTreeUtil.getParentOfType(methodCall, PsiClass.class);
    mySameClass = (callingClass == myMethod.getContainingClass());
    myCallingMethod = PsiTreeUtil.getParentOfType(myMethodCall, PsiMethod.class);
  }

  void run() throws IncorrectOperationException {
    int parameterIndex = myMethod.getParameterList().getParameterIndex(myParameter);
    myLocalReplacements = new HashMap<PsiVariable, PsiElement>();
    final PsiExpression[] arguments = myMethodCall.getArgumentList().getExpressions();
    for(int i=0; i<arguments.length; i++) {
      if (i != parameterIndex && arguments [i] instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)arguments[i];
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
          final PsiParameter param = myMethod.getParameterList().getParameters()[i];
          final PsiExpression paramRef =
            JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory().createExpressionFromText(param.getName(), myMethod);
          myLocalReplacements.put((PsiVariable) element, paramRef);
        }
      }
    }

    processParameterInitializer();

    PsiExpression initializerInMethod = (PsiExpression) myInitializer.copy();
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<PsiElement, PsiElement>();
    final boolean canEvaluate = replaceLocals(initializerInMethod, elementsToReplace);
    if (!canEvaluate) {
      CommonRefactoringUtil.showErrorHint(myMethod.getProject(), myEditor,
                                          "Parameter initializer depends on values which are not available inside the method and cannot be inlined",
                                          RefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    final Collection<PsiReference> parameterRefs = ReferencesSearch.search(myParameter).findAll();

    initializerInMethod = (PsiExpression) RefactoringUtil.replaceElementsWithMap(initializerInMethod, elementsToReplace);

    String question = RefactoringBundle.message("inline.parameter.confirmation", myParameter.getName(),
                                                initializerInMethod.getText());
    boolean createLocal;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      createLocal = myMethod.getProject().getUserData(CREATE_LOCAL_FOR_TESTS);
    }
    else {
      InlineParameterDialog dlg =
        new InlineParameterDialog(InlineParameterHandler.REFACTORING_NAME, question, HelpID.INLINE_VARIABLE, "OptionPane.questionIcon",
                                  true, myMethod.getProject());
      if (!dlg.showDialog()) {
        return;
      }
      createLocal = dlg.isCreateLocal();
    }
    performRefactoring(initializerInMethod, parameterRefs, createLocal);
  }
  public static final Key<Boolean> CREATE_LOCAL_FOR_TESTS = Key.create("CREATE_INLINE_PARAMETER_LOCAL_FOR_TESTS");

  private void processParameterInitializer() {
    myInitializer.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiLocalVariable) {
          final PsiLocalVariable localVariable = (PsiLocalVariable)element;
          if (myLocalReplacements.containsKey(localVariable)) return;
          final PsiElement[] elements = DefUseUtil.getDefs(myCallingMethod.getBody(), localVariable, expression);
          if (elements.length == 1) {
            PsiExpression localInitializer = null;
            if (elements [0] instanceof PsiLocalVariable) {
              localInitializer = ((PsiLocalVariable) elements [0]).getInitializer();
            }
            else if (elements [0] instanceof PsiAssignmentExpression) {
              localInitializer = ((PsiAssignmentExpression) elements [0]).getRExpression();
            }
            if (localInitializer != null) {
              if (InlineToAnonymousConstructorProcessor.isConstant(localInitializer)) {
                myLocalReplacements.put(localVariable, localInitializer);
              }
              else {
                final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<PsiElement, PsiElement>();
                PsiExpression replacedInitializer = (PsiExpression)localInitializer.copy();
                if (replaceLocals(replacedInitializer, elementsToReplace)) {
                  try {
                    replacedInitializer = (PsiExpression) RefactoringUtil.replaceElementsWithMap(replacedInitializer, elementsToReplace);
                  }
                  catch (IncorrectOperationException e) {
                    LOG.error(e);
                  }
                  myLocalReplacements.put(localVariable, replacedInitializer);
                }
              }
            }
          }
        }
      }
    });
  }

  private void performRefactoring(final PsiExpression initializerInMethod, final Collection<PsiReference> parameterRefs,
                                  final boolean createLocal) {
    final Collection<PsiFile> containingFiles = new HashSet<PsiFile>();
    containingFiles.add(myMethod.getContainingFile());
    containingFiles.add(myMethodCall.getContainingFile());

    final Project project = myMethod.getProject();
    new WriteCommandAction(project,
                           RefactoringBundle.message("inline.parameter.command.name", myParameter.getName()),
                           containingFiles.toArray(new PsiFile[containingFiles.size()])) {
      protected void run(final Result result) throws Throwable {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
        if (!createLocal) {
          for(PsiReference ref: parameterRefs) {
            InlineUtil.inlineVariable(myParameter, initializerInMethod, (PsiJavaCodeReferenceElement) ref.getElement());
          }
        }
        PsiDeclarationStatement localDeclaration = factory.createVariableDeclarationStatement(myParameter.getName(),
                                                                                              myParameter.getType(),
                                                                                              initializerInMethod);
        boolean parameterIsFinal = myParameter.hasModifierProperty(PsiModifier.FINAL);
        SameParameterValueInspection.InlineParameterValueFix.removeParameter(myMethod, myParameter);
        if (createLocal) {
          final PsiLocalVariable declaredVar = (PsiLocalVariable) localDeclaration.getDeclaredElements()[0];
          PsiUtil.setModifierProperty(declaredVar, PsiModifier.FINAL, parameterIsFinal);
          final PsiCodeBlock body = myMethod.getBody();
          if (body != null) {
            body.addAfter(localDeclaration, body.getLBrace());
          }
        }

        for(PsiVariable var: myLocalReplacements.keySet()) {
          if (ReferencesSearch.search(var).findFirst() == null) {
            var.delete();
          }
        }
      }

      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.DEFAULT;
      }
    }.execute();
  }

  private boolean replaceLocals(final PsiExpression expression,
                                final Map<PsiElement, PsiElement> elementsToReplace) {
    final Ref<Boolean> refCannotEvaluate = new Ref<Boolean>();
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (!canEvaluate(expression, element, elementsToReplace)) {
          refCannotEvaluate.set(Boolean.TRUE);
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression thisExpression) {
        super.visitThisExpression(thisExpression);
        final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
        PsiElement containingClass;
        if (qualifier != null) {
          containingClass = qualifier.resolve();
        } else {
          containingClass = PsiTreeUtil.getParentOfType(myMethodCall, PsiClass.class);
        }
        final PsiClass methodContainingClass = myMethod.getContainingClass();
        LOG.assertTrue(methodContainingClass != null);
        if (!PsiTreeUtil.isAncestor(containingClass, methodContainingClass, false)) {
          refCannotEvaluate.set(Boolean.TRUE);
        }
      }
    });
    return refCannotEvaluate.isNull();
  }

  private boolean canEvaluate(final PsiReferenceExpression expression,
                              final PsiElement element,
                              final Map<PsiElement, PsiElement> elementsToReplace) {
    if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
      final PsiVariable localVariable = (PsiVariable)element;
      final PsiElement localReplacement = myLocalReplacements.get(localVariable);
      if (localReplacement != null) {
        elementsToReplace.put(expression, localReplacement);
        return true;
      }
    }
    else if (element instanceof PsiMethod || element instanceof PsiField) {
      return mySameClass || ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC);
    }
    else if (element instanceof PsiClass) {
      return true;
    }
    return false;
  }
}