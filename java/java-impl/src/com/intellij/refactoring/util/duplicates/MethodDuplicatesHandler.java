/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class MethodDuplicatesHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  public static final String REFACTORING_NAME = RefactoringBundle.message("replace.method.code.duplicates.title");
  private static final Logger LOG = Logger.getInstance(MethodDuplicatesHandler.class);

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return getCannotRefactorMessage(PsiTreeUtil.getParentOfType(element, PsiMember.class)) == null;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    final PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
    final String cannotRefactorMessage = getCannotRefactorMessage(member);
    if (cannotRefactorMessage != null) {
      String message = RefactoringBundle.getCannotRefactorMessage(cannotRefactorMessage);
      showErrorMessage(message, project, editor);
      return;
    }

    final AnalysisScope scope = new AnalysisScope(file);
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    final BaseAnalysisActionDialog dlg =
      new BaseAnalysisActionDialog(RefactoringBundle.message("replace.method.duplicates.scope.chooser.title", REFACTORING_NAME),
                                   RefactoringBundle.message("replace.method.duplicates.scope.chooser.message"),
                                   project, scope, module, false,
                                   AnalysisUIOptions.getInstance(project), element);
    if (dlg.showAndGet()) {
      AnalysisScope selectedScope = dlg.getScope(AnalysisUIOptions.getInstance(project), scope, project, module);
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Locate duplicates", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          invokeOnScope(project, member, selectedScope);
        }
      });
    }
  }

  @Nullable
  private static String getCannotRefactorMessage(PsiMember member) {
    if (member == null) {
      return RefactoringBundle.message("locate.caret.inside.a.method");
    }
    if (member instanceof PsiMethod) {
      if (((PsiMethod)member).isConstructor()) {
        return RefactoringBundle.message("replace.with.method.call.does.not.work.for.constructors");
      }
      final PsiCodeBlock body = ((PsiMethod)member).getBody();
      if (body == null) {
        return RefactoringBundle.message("method.does.not.have.a.body", member.getName());
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return RefactoringBundle.message("method.has.an.empty.body", member.getName());
      }
    } else if (member instanceof PsiField) {
      final PsiField field = (PsiField)member;
      if (field.getInitializer() == null) {
        return "Field " + member.getName() + " doesn't have initializer";
      }
      final PsiClass containingClass = field.getContainingClass();
      if (!field.hasModifierProperty(PsiModifier.FINAL) || !field.hasModifierProperty(PsiModifier.STATIC) ||
          containingClass == null || containingClass.getQualifiedName() == null) {
        return "Replace Duplicates works with constants only";
      }
    } else {
      return "Caret should be inside method or constant";
    }
    return null;
  }

  public static void invokeOnScope(final Project project, final PsiMember member, final AnalysisScope scope) {
    invokeOnScope(project, Collections.singleton(member), scope, false);
  }

  public static void invokeOnScope(final Project project, final Set<PsiMember> members, final AnalysisScope scope, boolean silent) {
    final Map<PsiMember, List<Match>> duplicates = new HashMap<>();
    final int fileCount = scope.getFileCount();
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(false);
    }

    final Map<PsiMember, Set<Module>> memberWithModulesMap = new HashMap<>();
    for (final PsiMember member : members) {
      final Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(member));
      if (module != null) {
        final HashSet<Module> dependencies = new HashSet<>();
        ApplicationManager.getApplication().runReadAction(() -> ModuleUtilCore.collectModulesDependsOn(module, dependencies));
        memberWithModulesMap.put(member, dependencies);
      }
    }

    scope.accept(new PsiRecursiveElementVisitor() {
      private int myFileCount;
      @Override public void visitFile(final PsiFile file) {
        if (progressIndicator != null){
          if (progressIndicator.isCanceled()) return;
          progressIndicator.setFraction(((double)myFileCount++)/fileCount);
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          }
        }
        final Module targetModule = ModuleUtilCore.findModuleForPsiElement(file);
        if (targetModule == null) return;
        for (Map.Entry<PsiMember, Set<Module>> entry : memberWithModulesMap.entrySet()) {
          final Set<Module> dependencies = entry.getValue();
          if (dependencies == null || !dependencies.contains(targetModule)) continue;

          final PsiMember method = entry.getKey();
          final List<Match> matchList = hasDuplicates(file, method);
          for (Iterator<Match> iterator = matchList.iterator(); iterator.hasNext(); ) {
            Match match = iterator.next();
            final PsiElement matchStart = match.getMatchStart();
            final PsiElement matchEnd = match.getMatchEnd();
            for (PsiMember psiMember : members) {
              if (PsiTreeUtil.isAncestor(psiMember, matchStart, false) ||
                  PsiTreeUtil.isAncestor(psiMember, matchEnd, false)) {
                iterator.remove();
                break;
              }
            }
          }
          if (!matchList.isEmpty()) {
            List<Match> matches = duplicates.get(method);
            if (matches == null) {
              matches = new ArrayList<>();
              duplicates.put(method, matches);
            }
            matches.addAll(matchList);
          }
        }
      }
    });
    if (duplicates.isEmpty()) {
      if (!silent) {
        final Runnable nothingFoundRunnable = () -> {
          final String message = RefactoringBundle.message("idea.has.not.found.any.code.that.can.be.replaced.with.method.call",
                                                           ApplicationNamesInfo.getInstance().getProductName());
          Messages.showInfoMessage(project, message, REFACTORING_NAME);
        };
        if (ApplicationManager.getApplication().isUnitTestMode()) {
          nothingFoundRunnable.run();
        } else {
          ApplicationManager.getApplication().invokeLater(nothingFoundRunnable, project.getDisposed());
        }
      }
    } else {
      replaceDuplicate(project, duplicates, members);
    }
  }

  private static void replaceDuplicate(final Project project, final Map<PsiMember, List<Match>> duplicates, final Set<PsiMember> methods) {
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null && progressIndicator.isCanceled()) return;

    final Runnable replaceRunnable = () -> {
      LocalHistoryAction a = LocalHistory.getInstance().startAction(REFACTORING_NAME);
      try {
        for (final PsiMember member : methods) {
          final List<Match> matches = duplicates.get(member);
          if (matches == null) continue;
          final int duplicatesNo = matches.size();
          WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
          CommandProcessor.getInstance().executeCommand(project,
                                                        () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(() -> {
                                                          final MatchProvider matchProvider =
                                                            member instanceof PsiMethod ? new MethodDuplicatesMatchProvider((PsiMethod)member, matches)
                                                                                        : new ConstantMatchProvider(member, project, matches);
                                                          DuplicatesImpl.invoke(project, matchProvider);
                                                        }), REFACTORING_NAME, REFACTORING_NAME);

          WindowManager.getInstance().getStatusBar(project).setInfo("");
        }
      }
      finally {
        a.finish();
      }
    };
    ApplicationManager.getApplication().invokeLater(replaceRunnable, project.getDisposed());
  }

  public static List<Match> hasDuplicates(final PsiFile file, final PsiMember member) {
    final DuplicatesFinder duplicatesFinder = createDuplicatesFinder(member);
    if (duplicatesFinder == null) {
      return Collections.emptyList();
    }

    return duplicatesFinder.findDuplicates(file);
  }

  @Nullable
  public static DuplicatesFinder createDuplicatesFinder(PsiMember member) {
    PsiElement[] pattern;
    ReturnValue matchedReturnValue = null;
    if (member instanceof PsiMethod) {
      final PsiCodeBlock body = ((PsiMethod)member).getBody();
      LOG.assertTrue(body != null);
      final PsiStatement[] statements = body.getStatements();
      pattern = statements;
      matchedReturnValue = null;
      if (statements.length != 1 || !(statements[0] instanceof PsiReturnStatement)) {
        final PsiStatement lastStatement = statements.length > 0 ? statements[statements.length - 1] : null;
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
    } else {
      pattern = new PsiElement[]{((PsiField)member).getInitializer()};
    }
    if (pattern.length == 0) {
      return null;
    }
    final List<? extends PsiVariable> inputVariables = 
      member instanceof PsiMethod ? Arrays.asList(((PsiMethod)member).getParameterList().getParameters()) : new ArrayList<>();
    return new DuplicatesFinder(pattern,
                                new InputVariables(inputVariables, member.getProject(), new LocalSearchScope(pattern), false),
                                matchedReturnValue,
                                new ArrayList<>());
  }

  static String getStatusMessage(final int duplicatesNo) {
    return RefactoringBundle.message("method.duplicates.found.message", duplicatesNo);
  }

  private static void showErrorMessage(String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.METHOD_DUPLICATES);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }
}
