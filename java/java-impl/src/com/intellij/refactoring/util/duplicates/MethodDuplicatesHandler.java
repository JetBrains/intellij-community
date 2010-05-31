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
package com.intellij.refactoring.util.duplicates;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author dsl
 */
public class MethodDuplicatesHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.method.code.duplicates.title");
  private static final Logger LOG = Logger.getInstance("#" + MethodDuplicatesHandler.class.getName());

  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("locate.caret.inside.a.method"));
      showErrorMessage(message, project, editor);
      return;
    }
    if (method.isConstructor()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("replace.with.method.call.does.not.work.for.constructors"));
      showErrorMessage(message, project, editor);
    }
    final PsiCodeBlock body = method.getBody();
    if (body == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.does.not.have.a.body", method.getName()));
      showErrorMessage(message, project, editor);
      return;
    }
    final PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("method.has.an.empty.body", method.getName()));

      showErrorMessage(message, project, editor);
      return;
    }
    final AnalysisScope scope = new AnalysisScope(file);
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(RefactoringBundle.message("replace.method.duplicates.scope.chooser.title", REFACTORING_NAME),
                                                                RefactoringBundle.message("replace.method.duplicates.scope.chooser.message"),
                                                                project, scope, module != null ? module.getName() : null, false,
                                                                AnalysisUIOptions.getInstance(project), element);
    dlg.show();
    if (dlg.isOK()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
          invokeOnScope(project, method, dlg.getScope(AnalysisUIOptions.getInstance(project), scope, project, module));
        }
      }, "Locate method duplicates", true, project) ;
    }
  }

  public static void invokeOnScope(final Project project, final PsiMethod method, final AnalysisScope scope) {
    final List<Match> duplicates = new ArrayList<Match>();
    scope.accept(new PsiRecursiveElementVisitor() {
      @Override public void visitFile(final PsiFile file) {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null && progressIndicator.isCanceled()) return;
        duplicates.addAll(hasDuplicates(file, method));
      }
    });
    replaceDuplicate(project, duplicates, method);
    final Runnable nothingFoundRunnable = new Runnable() {
      public void run() {
        if (duplicates.isEmpty()) {
          final String message = RefactoringBundle.message("idea.has.not.found.any.code.that.can.be.replaced.with.method.call",
                                                           ApplicationNamesInfo.getInstance().getProductName());
          Messages.showInfoMessage(project, message, REFACTORING_NAME);
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      nothingFoundRunnable.run();
    } else {
      ApplicationManager.getApplication().invokeLater(nothingFoundRunnable, ModalityState.NON_MODAL);
    }
  }

  private static void replaceDuplicate(final Project project, final List<Match> duplicates, final PsiMethod method) {
    LocalHistoryAction a = LocalHistory.getInstance().startAction(REFACTORING_NAME);
    try {
      final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null && progressIndicator.isCanceled()) return;

      final Runnable replaceRunnable = new Runnable() {
        public void run() {
          final int duplicatesNo = duplicates.size();
          WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            public void run() {
              PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(new Runnable() {
                public void run() {
                  DuplicatesImpl.invoke(project, new MethodDuplicatesMatchProvider(method, duplicates));
                }
              });
            }
          }, REFACTORING_NAME, REFACTORING_NAME);

          WindowManager.getInstance().getStatusBar(project).setInfo("");
        }
      };
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        replaceRunnable.run();
      }
      else {
        ApplicationManager.getApplication().invokeLater(replaceRunnable, ModalityState.NON_MODAL);
      }
    }
    finally {
      a.finish();
    }
  }

  public static List<Match> hasDuplicates(final PsiFile file, final PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    LOG.assertTrue(body != null);
    final PsiStatement[] statements = body.getStatements();
    PsiElement[] pattern = statements;
    ReturnValue matchedReturnValue = null;
    if (statements.length != 1 || !(statements[0] instanceof PsiReturnStatement)) {
      final PsiStatement lastStatement = statements[statements.length - 1];
      if (lastStatement instanceof PsiReturnStatement) {
        final PsiExpression returnValue = ((PsiReturnStatement)lastStatement).getReturnValue();
        if (returnValue instanceof PsiReferenceExpression) {
          final PsiElement resolved = ((PsiReferenceExpression)returnValue).resolve();
          if (resolved instanceof PsiVariable) {
            pattern = new PsiElement[statements.length - 1];
            System.arraycopy(statements, 0, pattern, 0, statements.length - 1);
            matchedReturnValue = new VariableReturnValue((PsiVariable)resolved);
          }
        }
      }
    } else {
      final PsiExpression returnValue = ((PsiReturnStatement)statements[0]).getReturnValue();
      if (returnValue != null) {
        pattern = new PsiElement[]{returnValue};
      }
    }
    final DuplicatesFinder duplicatesFinder =
      new DuplicatesFinder(pattern, 
                           new InputVariables(Arrays.asList(method.getParameterList().getParameters()), method.getProject(), new LocalSearchScope(pattern), false), matchedReturnValue,
                           new ArrayList<PsiVariable>());

    return duplicatesFinder.findDuplicates(file);
  }

  static String getStatusMessage(final int duplicatesNo) {
    return RefactoringBundle.message("method.duplicates.found.message", duplicatesNo);
  }

  private static void showErrorMessage(String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.METHOD_DUPLICATES);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static class MethodDuplicatesMatchProvider implements MatchProvider {
    private final PsiMethod myMethod;
    private final List<Match> myDuplicates;

    private MethodDuplicatesMatchProvider(PsiMethod method, List<Match> duplicates) {
      myMethod = method;
      myDuplicates = duplicates;
    }

    public PsiElement processMatch(Match match) throws IncorrectOperationException {
      match.changeSignature(myMethod);
      final PsiClass containingClass = myMethod.getContainingClass();
      if (isEssentialStaticContextAbsent(match)) {
        PsiUtil.setModifierProperty(myMethod, PsiModifier.STATIC, true);
      }

      final PsiElementFactory factory = JavaPsiFacade.getInstance(myMethod.getProject()).getElementFactory();
      final boolean needQualifier = match.getInstanceExpression() != null;
      final boolean needStaticQualifier = isExternal(match);
      final boolean nameConflicts = nameConflicts(match);
      @NonNls final String text = needQualifier || needStaticQualifier || nameConflicts
                                  ?  "q." + myMethod.getName() + "()": myMethod.getName() + "()";
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)factory.createExpressionFromText(text, null);
      methodCallExpression = (PsiMethodCallExpression)CodeStyleManager.getInstance(myMethod.getManager()).reformat(methodCallExpression);
      final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      for (final PsiParameter parameter : parameters) {
        final List<PsiElement> parameterValue = match.getParameterValues(parameter);
        if (parameterValue != null) {
          for (PsiElement val : parameterValue) {
            methodCallExpression.getArgumentList().add(val);
          }
        }
        else {
          methodCallExpression.getArgumentList().add(factory.createExpressionFromText(PsiTypesUtil.getDefaultValueOfType(parameter.getType()), parameter));
        }
      }
      if (needQualifier || needStaticQualifier || nameConflicts) {
        final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
        LOG.assertTrue(qualifierExpression != null);
        if (needQualifier) {
          qualifierExpression.replace(match.getInstanceExpression());
        } else if (needStaticQualifier || myMethod.hasModifierProperty(PsiModifier.STATIC)) {
          qualifierExpression.replace(factory.createReferenceExpression(containingClass));
        } else {
          final PsiClass psiClass = PsiTreeUtil.getParentOfType(match.getMatchStart(), PsiClass.class);
          if (psiClass != null && psiClass.isInheritor(containingClass, true)) {
            qualifierExpression.replace(RefactoringUtil.createSuperExpression(containingClass.getManager(), psiClass));
          } else {
            qualifierExpression.replace(RefactoringUtil.createThisExpression(containingClass.getManager(), containingClass));
          }
        }
      }
      VisibilityUtil.escalateVisibility(myMethod, match.getMatchStart());
      final PsiCodeBlock body = myMethod.getBody();
      assert body != null;
      final PsiStatement[] statements = body.getStatements();
      if (statements[statements.length - 1] instanceof PsiReturnStatement) {
        final PsiExpression value = ((PsiReturnStatement)statements[statements.length - 1]).getReturnValue();
        if (value instanceof PsiReferenceExpression) {
          final PsiElement var = ((PsiReferenceExpression)value).resolve();
          if (var instanceof PsiVariable) {
            match.replace(myMethod, methodCallExpression, (PsiVariable)var);
            return methodCallExpression;
          }
        }
      }
      return match.replace(myMethod, methodCallExpression, null);
    }



    private boolean isExternal(final Match match) {
      final PsiElement matchStart = match.getMatchStart();
      final PsiClass containingClass = myMethod.getContainingClass();
      if (PsiTreeUtil.isAncestor(containingClass, matchStart, false)) {
        return false;
      }
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(matchStart, PsiClass.class);
      if (psiClass != null) {
        if (InheritanceUtil.isInheritorOrSelf(psiClass, containingClass, true)) return false;
      }
      return true;
    }

    private boolean nameConflicts(Match match) {
      PsiClass matchClass = PsiTreeUtil.getParentOfType(match.getMatchStart(), PsiClass.class);
      while (matchClass != null && matchClass != myMethod.getContainingClass()) {
        if (matchClass.findMethodsBySignature(myMethod, false).length > 0) {
          return true;
        }
        matchClass = PsiTreeUtil.getParentOfType(matchClass, PsiClass.class);
      }
      return false;
    }

    private boolean isEssentialStaticContextAbsent(final Match match) {
      if (!myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiExpression instanceExpression = match.getInstanceExpression();
        if (instanceExpression != null) return false;
        if (isExternal(match)) return true;
        if (PsiTreeUtil.isAncestor(myMethod.getContainingClass(), match.getMatchStart(), false) && RefactoringUtil.isInStaticContext(match.getMatchStart(), myMethod.getContainingClass())) return true;
      }
      return false;
    }

    public List<Match> getDuplicates() {
      return myDuplicates;
    }

    public boolean hasDuplicates() {
      return myDuplicates.isEmpty();
    }

    @NotNull
    public String getConfirmDuplicatePrompt(final Match match) {
      final PsiElement matchStart = match.getMatchStart();
      @Modifier String visibility = VisibilityUtil.getPossibleVisibility(myMethod, matchStart);
      final boolean shouldBeStatic = isEssentialStaticContextAbsent(match);
      final String signature = match.getChangedSignature(myMethod, myMethod.hasModifierProperty(PsiModifier.STATIC) || shouldBeStatic, visibility);
      if (signature != null) {
        return RefactoringBundle.message("replace.this.code.fragment.and.change.signature", signature);
      }
      final boolean needToEscalateVisibility = !PsiUtil.isAccessible(myMethod, matchStart, null);
      if (needToEscalateVisibility) {
        final String visibilityPresentation = VisibilityUtil.toPresentableText(visibility);
        return shouldBeStatic
               ? RefactoringBundle.message("replace.this.code.fragment.and.make.method.static.visible", visibilityPresentation)
               : RefactoringBundle.message("replace.this.code.fragment.and.make.method.visible", visibilityPresentation);
      }
      if (shouldBeStatic) {
        return RefactoringBundle.message("replace.this.code.fragment.and.make.method.static");
      }
      return RefactoringBundle.message("replace.this.code.fragment");
    }
  }
}
