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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 13:36:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.*;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.refactoring.ui.NameSuggestionsGenerator;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.occurences.ExpressionOccurenceManager;
import com.intellij.usageView.UsageInfo;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.List;


public class IntroduceParameterHandler extends IntroduceHandlerBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceParameter.IntroduceParameterHandler");
  static final String REFACTORING_NAME = RefactoringBundle.message("introduce.parameter.title");
  private Project myProject;

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    ElementToWorkOn.processElementToWorkOn(editor, file, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER, project, new Pass<ElementToWorkOn>() {
      @Override
      public void pass(final ElementToWorkOn elementToWorkOn) {
        if (elementToWorkOn == null) return;

        final PsiExpression expr = elementToWorkOn.getExpression();
        final PsiLocalVariable localVar = elementToWorkOn.getLocalVariable();
        final boolean isInvokedOnDeclaration = elementToWorkOn.isInvokedOnDeclaration();

        if (invoke(editor, project, expr, localVar, isInvokedOnDeclaration)) {
          editor.getSelectionModel().removeSelection();
        }
      }
    });
  }

  protected boolean invokeImpl(Project project, PsiExpression tempExpr, Editor editor) {
    return invoke(editor, project, tempExpr, null, false);
  }

  protected boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
    return invoke(editor, project, null, localVariable, true);
  }

  private boolean invoke(Editor editor, Project project, final PsiExpression expr,
                         PsiLocalVariable localVar, boolean invokedOnDeclaration) {
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).hasUncommitedDocuments());
    PsiMethod method;
    if (expr != null) {
      final PsiElement physicalElement = expr.getUserData(ElementToWorkOn.PARENT);
      method = Util.getContainingMethod(physicalElement != null ? physicalElement : expr);
    }
    else {
      method = Util.getContainingMethod(localVar);
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("expression:" + expr);
    }

    myProject = project;
    if (expr == null && localVar == null) {
      String message =  RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.block.should.represent.an.expression"));
      showErrorMessage(myProject, message, editor);
      return false;
    }


    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context", REFACTORING_NAME));
      showErrorMessage(myProject, message, editor);
      return false;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) return false;

    final PsiType typeByExpression = invokedOnDeclaration ? null : RefactoringUtil.getTypeByExpressionWithExpectedType(expr);
    if (!invokedOnDeclaration && typeByExpression == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("type.of.the.selected.expression.cannot.be.determined"));
      showErrorMessage(myProject, message, editor);
      return false;
    }

    if (!invokedOnDeclaration && PsiType.VOID.equals(typeByExpression)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selected.expression.has.void.type"));
      showErrorMessage(project, message, editor);
      return false;
    }

    method = chooseEnclosingMethod(method);
    if (method == null) return false;

    final PsiMethod methodToSearchFor = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringBundle.message("to.refactor"));
    if (methodToSearchFor == null) return false;
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, methodToSearchFor)) return false;

    PsiExpression[] occurences;
    if (expr != null) {
      occurences = new ExpressionOccurenceManager(expr, method, null).findExpressionOccurrences();
    }
    else { // local variable
      occurences = CodeInsightUtil.findReferenceExpressions(method, localVar);
    }
    PsiExpression expressionToRemoveParamFrom = expr;
    if (expr == null) {
      expressionToRemoveParamFrom = localVar.getInitializer();
    }
    TIntArrayList parametersToRemove = expressionToRemoveParamFrom == null ? new TIntArrayList() : Util.findParametersToRemove(method, expressionToRemoveParamFrom, occurences);

    boolean mustBeFinal = false;
    if (localVar != null) {
      for(PsiExpression occurrence: occurences) {
        if (PsiTreeUtil.getParentOfType(occurrence, PsiClass.class, PsiMethod.class) != method) {
          mustBeFinal = true;
          break;
        }
      }
    }

    List<UsageInfo> localVars = new ArrayList<UsageInfo>();
    List<UsageInfo> classMemberRefs = new ArrayList<UsageInfo>();
    List<UsageInfo> params = new ArrayList<UsageInfo>();


    if (expr != null) {
      Util.analyzeExpression(expr, localVars, classMemberRefs, params);
    }

    if (expr instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
      if (resolved instanceof PsiLocalVariable) {
        localVar = (PsiLocalVariable) resolved;
      }
    }


    if (ApplicationManager.getApplication().isUnitTestMode()) {
      @NonNls String parameterName = "anObject";
      boolean replaceAllOccurences = true;
      boolean isDeleteLocalVariable = true;
      PsiExpression initializer = localVar != null && expr == null ? localVar.getInitializer() : expr;
      new IntroduceParameterProcessor(myProject, method, methodToSearchFor, initializer, expr, localVar, isDeleteLocalVariable, parameterName,
                                      replaceAllOccurences, IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE, mustBeFinal,
                                      false, null,
                                      parametersToRemove).run();
    }
    else {
      final String propName = localVar != null ? JavaCodeStyleManager.getInstance(myProject).variableNameToPropertyName(localVar.getName(), VariableKind.LOCAL_VARIABLE) : null;
      final PsiType initializerType = IntroduceParameterProcessor.getInitializerType(null, expr, localVar);

      TypeSelectorManagerImpl typeSelectorManager = expr != null
                                                ? new TypeSelectorManagerImpl(project, initializerType, expr, occurences)
                                                : new TypeSelectorManagerImpl(project, initializerType, occurences);

      NameSuggestionsGenerator nameSuggestionsGenerator = new NameSuggestionsGenerator() {
        public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
          final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(myProject);
          final SuggestedNameInfo info = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, propName, expr, type);
          final String[] strings = JavaCompletionUtil.completeVariableNameForRefactoring(codeStyleManager, type, VariableKind.LOCAL_VARIABLE, info);
          return new SuggestedNameInfo.Delegate(strings, info);
        }

      };
      boolean isInplaceAvailableOnDataContext = editor != null && editor.getSettings().isVariableInplaceRenameEnabled()
                                                && method == methodToSearchFor && method.hasModifierProperty(PsiModifier.PRIVATE) &&
                                                parametersToRemove.isEmpty() && (localVar == null || expr == null) &&
                                                !Util.anyFieldsWithGettersPresent(classMemberRefs);

      if (!isInplaceAvailableOnDataContext) {
        if (editor != null) {
          RefactoringUtil.highlightAllOccurences(myProject, occurences, editor);
        }
        new IntroduceParameterDialog(myProject, classMemberRefs, occurences.length, localVar, expr, nameSuggestionsGenerator,
                                     typeSelectorManager, methodToSearchFor, method, parametersToRemove, mustBeFinal).show();
      } else {
        new InplaceIntroduceParameterPopup(project, editor,
                                     typeSelectorManager, nameSuggestionsGenerator,
                                     expr, localVar, method, methodToSearchFor, occurences, parametersToRemove,
                                     mustBeFinal).inplaceIntroduceParameter();
      }
    }
    return true;
  }

  private static void showErrorMessage(Project project, String message, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INTRODUCE_PARAMETER);
  }


  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    // Never called
    /* do nothing */
  }

  private static List<PsiMethod> getEnclosingMethods(PsiMethod nearest) {
    List<PsiMethod> enclosingMethods = new ArrayList<PsiMethod>();
    enclosingMethods.add(nearest);
    PsiMethod method = nearest;
    while(true) {
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, true);
      if (method == null) break;
      enclosingMethods.add(method);
    }
    if (enclosingMethods.size() > 1) {
      List<PsiMethod> methodsNotImplementingLibraryInterfaces = new ArrayList<PsiMethod>();
      for(PsiMethod enclosing: enclosingMethods) {
        PsiMethod[] superMethods = enclosing.findDeepestSuperMethods();
        boolean libraryInterfaceMethod = false;
        for(PsiMethod superMethod: superMethods) {
          libraryInterfaceMethod |= isLibraryInterfaceMethod(superMethod);
        }
        if (!libraryInterfaceMethod) {
          methodsNotImplementingLibraryInterfaces.add(enclosing);
        }
      }
      if (methodsNotImplementingLibraryInterfaces.size() > 0) {
        return methodsNotImplementingLibraryInterfaces;
      }
    }
    return enclosingMethods;
  }


  @Nullable
  public static PsiMethod chooseEnclosingMethod(@NotNull PsiMethod method) {
    final List<PsiMethod> validEnclosingMethods = getEnclosingMethods(method);
    if (validEnclosingMethods.size() > 1 && !ApplicationManager.getApplication().isUnitTestMode()) {
      final EnclosingMethodSelectionDialog dialog = new EnclosingMethodSelectionDialog(method.getProject(), validEnclosingMethods);
      dialog.show();
      if (!dialog.isOK()) return null;
      method = dialog.getSelectedMethod();
    }
    else if (validEnclosingMethods.size() == 1) {
      method = validEnclosingMethods.get(0);
    }
    return method;
  }

  private static boolean isLibraryInterfaceMethod(final PsiMethod method) {
    return method.hasModifierProperty(PsiModifier.ABSTRACT) && !method.getManager().isInProject(method);
  }
}
