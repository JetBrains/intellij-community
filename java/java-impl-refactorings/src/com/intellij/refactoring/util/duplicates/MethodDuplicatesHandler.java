// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
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
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MethodDuplicatesHandler implements RefactoringActionHandler, ContextAwareActionHandler {
  private static final Logger LOG = Logger.getInstance(MethodDuplicatesHandler.class);

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    PsiMember member = findMember(editor, file);
    return member != null && getCannotRefactorMessage(member) == null;
  }

  private static @Nullable PsiMember findMember(@Nullable Editor editor, @Nullable PsiFile file) {
    if (editor == null || file == null) return null;
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PsiMember member = MethodUtils.getJavaMethodFromHeader(element);
    if (member == null) {
      member = PsiTreeUtil.getParentOfType(element, PsiField.class);
    }
    return member;
  }

  public static @NlsActions.ActionText String getActionName(DataContext dataContext){
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project != null && editor != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      PsiMember member = findMember(editor, file);
      if (member instanceof PsiField) {
        return ActionsBundle.message("action.MethodDuplicates.field.text");
      }
      else if (member instanceof PsiMethod) {
        return ActionsBundle.message("action.MethodDuplicates.method.text");
      }
    }
    return ActionsBundle.message("action.MethodDuplicates.text");
  }

  @Override
  public void invoke(final @NotNull Project project, final Editor editor, PsiFile file, DataContext dataContext) {
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
      new BaseAnalysisActionDialog(JavaRefactoringBundle.message("replace.method.duplicates.scope.chooser.title", getRefactoringName()),
                                   JavaRefactoringBundle.message("replace.method.duplicates.scope.chooser.message"), project, BaseAnalysisActionDialog.standardItems(project, scope, module, element),
                                   AnalysisUIOptions.getInstance(project), false);
    if (dlg.showAndGet()) {
      AnalysisScope selectedScope = dlg.getScope(scope);
      ProgressManager.getInstance().run(new Task.Backgroundable(project, JavaRefactoringBundle.message("locate.duplicates.action.name"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          invokeOnScope(project, member, selectedScope);
        }
      });
    }
  }

  private static @Nullable @NlsContexts.DialogMessage String getCannotRefactorMessage(PsiMember member) {
    if (member == null) {
      return JavaRefactoringBundle.message("locate.caret.inside.a.method");
    }
    if (member instanceof PsiMethod method) {
      if (method.isConstructor()) {
        return JavaRefactoringBundle.message("replace.with.method.call.does.not.work.for.constructors");
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return JavaRefactoringBundle.message("method.does.not.have.a.body", member.getName());
      }
      final PsiStatement[] statements = body.getStatements();
      if (statements.length == 0) {
        return JavaRefactoringBundle.message("method.has.an.empty.body", member.getName());
      }
    } else if (member instanceof PsiField field) {
      if (field.getInitializer() == null) {
        return JavaRefactoringBundle.message("dialog.message.field.doesnt.have.initializer", member.getName());
      }
      final PsiClass containingClass = field.getContainingClass();
      if (!field.hasModifierProperty(PsiModifier.FINAL) || !field.hasModifierProperty(PsiModifier.STATIC) ||
          containingClass == null || containingClass.getQualifiedName() == null) {
        return JavaRefactoringBundle.message("dialog.message.replace.duplicates.works.with.constants.only");
      }
    } else {
      return JavaRefactoringBundle.message("dialog.message.caret.should.be.inside.method.or.constant");
    }
    return null;
  }

  public static void invokeOnScope(final Project project, final PsiMember member, final AnalysisScope scope) {
    invokeOnScope(project, Collections.singleton(member), scope, false);
  }

  public static void invokeOnScope(final Project project, final Set<? extends PsiMember> members, final AnalysisScope scope, boolean silent) {
    ReadAction.nonBlocking(() -> computeProviderMap(project, members, scope))
      .expireWhen(() -> ContainerUtil.exists(members, m -> !m.isValid()))
      .finishOnUiThread(ModalityState.nonModal(), providerMap -> {
        if (providerMap.isEmpty()) {
          if (!silent) {
            final String message = JavaRefactoringBundle.message(
              "idea.has.not.found.any.code.that.can.be.replaced.with.method.call",
              ApplicationNamesInfo.getInstance().getProductName());
            Messages.showInfoMessage(project, message, getRefactoringName());
          }
        }
        else {
          replaceDuplicate(project, providerMap.values());
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @RequiresReadLock
  private static @NotNull Map<PsiMember, MatchProvider> computeProviderMap(@NotNull Project project,
                                                                           @NotNull Set<? extends PsiMember> members,
                                                                           @NotNull AnalysisScope scope) {
    final Map<PsiMember, List<Match>> duplicates = new HashMap<>();
    final int fileCount = scope.getFileCount();
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(false);
    }

    final Map<PsiMember, Set<Module>> memberWithModulesMap = new HashMap<>();
    for (final PsiMember member : members) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(member);
      if (module != null) {
        final HashSet<Module> dependencies = new HashSet<>();
        ModuleUtilCore.collectModulesDependsOn(module, dependencies);
        memberWithModulesMap.put(member, dependencies);
      }
    }

    scope.accept(new PsiRecursiveElementVisitor() {
      private int myFileCount;
      @Override public void visitFile(final @NotNull PsiFile psiFile) {
        if (progressIndicator != null){
          if (progressIndicator.isCanceled()) return;
          progressIndicator.setFraction(((double)myFileCount++)/fileCount);
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          if (virtualFile != null) {
            progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          }
        }
        final Module targetModule = ModuleUtilCore.findModuleForPsiElement(psiFile);
        if (targetModule == null) return;
        for (Map.Entry<PsiMember, Set<Module>> entry : memberWithModulesMap.entrySet()) {
          final Set<Module> dependencies = entry.getValue();
          if (dependencies == null || !dependencies.contains(targetModule)) continue;

          final PsiMember member = entry.getKey();
          final List<Match> matchList = hasDuplicates(psiFile, member);
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

            if (member instanceof PsiMethod method && method.findDeepestSuperMethods().length > 0 &&
                MethodDuplicatesMatchProvider.isEssentialStaticContextAbsent(match, method)) {
              iterator.remove();
            }
          }
          if (!matchList.isEmpty()) {
            duplicates.computeIfAbsent(member, __ -> new ArrayList<>()).addAll(matchList);
          }
        }
      }
    });
    return EntryStream.of(duplicates).mapToValue(MatchProvider::create).toMap();
  }

  /**
   * Replace duplicates previously computed and stored in {@link MatchProvider} objects.
   * May display UI for confirmation.
   * 
   * @param project project to work on
   * @param duplicates collection of precomputed {@link MatchProvider} objects
   * @see MatchProvider#create(PsiMember, List) 
   */
  @RequiresEdt
  public static void replaceDuplicate(@NotNull Project project, @NotNull Collection<MatchProvider> duplicates) {
    if (duplicates.isEmpty()) return;
    LocalHistoryAction a = LocalHistory.getInstance().startAction(getRefactoringName());
    try {
      for (MatchProvider matchProvider : duplicates) {
        final int duplicatesNo = matchProvider.getDuplicates().size();
        WindowManager.getInstance().getStatusBar(project).setInfo(getStatusMessage(duplicatesNo));
        CommandProcessor.getInstance().executeCommand(
          project,
          () -> PostprocessReformattingAspect.getInstance(project)
            .postponeFormattingInside(() -> DuplicatesImpl.invoke(project, matchProvider, true)), 
          getRefactoringName(), getRefactoringName());

        WindowManager.getInstance().getStatusBar(project).setInfo("");
      }
    }
    finally {
      a.finish();
    }
  }

  public static List<Match> hasDuplicates(final PsiElement file, final PsiMember member) {
    final DuplicatesFinder duplicatesFinder = createDuplicatesFinder(member);
    if (duplicatesFinder == null) {
      return Collections.emptyList();
    }

    return duplicatesFinder.findDuplicates(file);
  }

  private static @Nullable DuplicatesFinder createDuplicatesFinder(PsiMember member) {
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
                                new InputVariables(inputVariables, member.getProject(), new LocalSearchScope(pattern), false, Collections.emptySet()),
                                matchedReturnValue,
                                new ArrayList<>());
  }

  private static @NlsContexts.StatusBarText @NotNull String getStatusMessage(final int duplicatesNo) {
    return JavaRefactoringBundle.message("method.duplicates.found.message", duplicatesNo);
  }

  private static void showErrorMessage(@NlsContexts.DialogMessage String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.METHOD_DUPLICATES);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  public static @NlsContexts.ProgressTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("replace.method.code.duplicates.title");
  }
}
