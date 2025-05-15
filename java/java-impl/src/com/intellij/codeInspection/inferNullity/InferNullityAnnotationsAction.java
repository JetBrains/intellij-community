// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.inferNullity;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.quickfix.JetBrainsAnnotationsExternalLibraryResolver;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.java.JavaBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OkCancelDialogBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.util.*;

public class InferNullityAnnotationsAction extends BaseAnalysisAction {
  private static final String SUGGEST_ANNOTATION_DEPENDENCY = "java.suggest.annotation.dependency";
  private static final @NonNls String ANNOTATE_LOCAL_VARIABLES = "checkbox.annotate.local.variables";
  private InferNullityAdditionalUi myUi;

  public InferNullityAnnotationsAction() {
    super(JavaBundle.messagePointer("dialog.title.infer.nullity"), JavaBundle.messagePointer("action.title.infer.nullity.annotations"));
  }

  @Override
  protected void analyze(final @NotNull Project project, final @NotNull AnalysisScope scope) {
    try {
      doAnalysis(project, scope);
    }
    finally {
      dispose();
    }
  }

  @Override
  protected void canceled() {
    super.canceled();
    dispose();
  }

  private void doAnalysis(@NotNull Project project, @NotNull AnalysisScope scope) {
    PropertiesComponent.getInstance().setValue(ANNOTATE_LOCAL_VARIABLES, isAnnotateLocalVariables());

    final ProgressManager progressManager = ProgressManager.getInstance();
    final Set<Module> modulesWithoutAnnotations = new HashSet<>();
    final Set<Module> modulesWithLL = new HashSet<>();
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    final String defaultNullable = NullableNotNullManager.getInstance(project).getDefaultNullable();
    final int[] fileCount = new int[]{0};
    if (!progressManager.runProcessWithProgressSynchronously(() -> scope.accept(new PsiElementVisitor() {
      private final Set<Module> processed = new HashSet<>();

      @Override
      public void visitFile(@NotNull PsiFile psiFile) {
        fileCount[0]++;
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        final VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile != null) {
          progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
        }
        progressIndicator.setText(AnalysisBundle.message("scanning.scope.progress.title"));
        if (!(psiFile instanceof PsiJavaFile)) return;
        final Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
        if (module != null && processed.add(module)) {
          if (PsiUtil.getLanguageLevel(psiFile).compareTo(LanguageLevel.JDK_1_5) < 0) {
            modulesWithLL.add(module);
          }
          else if (javaPsiFacade.findClass(defaultNullable, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) == null) {
            modulesWithoutAnnotations.add(module);
          }
        }
      }
    }), JavaBundle.message("progress.title.check.applicability"), true, project)) {
      return;
    }
    if (!modulesWithLL.isEmpty()) {
      Messages.showErrorDialog(project, JavaBundle
                                 .message("dialog.message.infer.nullity.annotations.requires.the.project.language.level"),
                               JavaBundle.message("action.title.infer.nullity.annotations"));
      return;
    }
    if (!modulesWithoutAnnotations.isEmpty()) {
      addAnnotationsDependency(project, modulesWithoutAnnotations, defaultNullable,
                               JavaBundle.message("action.title.infer.nullity.annotations"))
        .onSuccess(__ -> {
          restartAnalysis(project, scope);
        });
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final UsageInfo[] usageInfos = findUsages(project, scope, fileCount[0]);
    if (usageInfos == null) return;

    processUsages(project, scope, usageInfos);
  }

  protected void processUsages(@NotNull Project project, @NotNull AnalysisScope scope, UsageInfo @NotNull [] usageInfos) {
    if (usageInfos.length < 5) {
      applyRunnable(project, () -> usageInfos).run();
    }
    else {
      showUsageView(project, usageInfos, scope);
    }
  }

  public static Promise<Void> addAnnotationsDependency(final @NotNull Project project,
                                                       final @NotNull Set<? extends Module> modulesWithoutAnnotations,
                                                       @NotNull String annoFQN, final @NlsContexts.DialogTitle @NotNull String title) {
    final Library annotationsLib = LibraryUtil.findLibraryByClass(annoFQN, project);
    if (annotationsLib != null) {
      String message = JavaBundle.message("dialog.message.modules.dont.refer.to.existing.annotations.library",
                                          modulesWithoutAnnotations.size(),
                                          StringUtil.join(modulesWithoutAnnotations, Module::getName, ", "),
                                          annotationsLib.getName());
      if (createDependencyDialog(title, message, project).ask(project)) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          for (Module module : modulesWithoutAnnotations) {
            ModuleRootModificationUtil.addDependency(module, annotationsLib);
          }
        });
        return Promises.resolvedPromise();
      }
      return Promises.rejectedPromise();
    }

    String message = JavaBundle.message("dialog.message.jetbrains.annotations.library.is.missing");
    if (createDependencyDialog(title, message, project).ask(project)) {
      Module firstModule = modulesWithoutAnnotations.iterator().next();
      return JavaProjectModelModificationService.getInstance(project)
        .addDependency(modulesWithoutAnnotations, JetBrainsAnnotationsExternalLibraryResolver.getAnnotationsLibraryDescriptor(firstModule),
                       DependencyScope.COMPILE);
    }
    return Promises.rejectedPromise();
  }

  private static @NotNull OkCancelDialogBuilder createDependencyDialog(@NlsContexts.DialogTitle @NotNull String title,
                                                                       @NlsContexts.DialogMessage @NotNull String message,
                                                                       @NotNull Project project) {
    return MessageDialogBuilder.okCancel(title, message)
      .icon(Messages.getErrorIcon())
      .doNotAsk(new DoNotAskOption.Adapter() {
        @Override
        public void rememberChoice(boolean isSelected, int exitCode) {
          PropertiesComponent.getInstance(project).setValue(SUGGEST_ANNOTATION_DEPENDENCY, !isSelected, true);
        }
      })
      .yesText(JavaBundle.message("button.add.dependency"));
  }

  protected UsageInfo @Nullable [] findUsages(final @NotNull Project project,
                                              final @NotNull AnalysisScope scope,
                                              final int fileCount) {
    final NullityInferrer inferrer = new NullityInferrer(isAnnotateLocalVariables(), project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final List<UsageInfo> usages = new ArrayList<>();
    final Runnable searchForUsages = () -> {
      scope.accept(new PsiElementVisitor() {
        int myFileCount;

        @Override
        public void visitFile(final @NotNull PsiFile psiFile) {
          myFileCount++;
          final VirtualFile virtualFile = psiFile.getVirtualFile();
          final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
          final Document document = viewProvider == null ? null : viewProvider.getDocument();
          if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
          final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          if (progressIndicator != null) {
            progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
            progressIndicator.setFraction(((double)myFileCount) / fileCount);
          }
          if (psiFile instanceof PsiJavaFile) {
            inferrer.collect(psiFile);
          }
        }
      });

      ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
      if (indicator != null) {
        indicator.setIndeterminate(true);
        indicator.setText(JavaBundle.message("infer.nullity.progress"));
      }

      inferrer.collect(usages);
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(searchForUsages, JavaBundle
        .message("action.description.infer.nullity.annotations"), true, project)) {
        return null;
      }
    }
    else {
      searchForUsages.run();
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  protected boolean isAnnotateLocalVariables() {
    return myUi.getCheckBox().isSelected();
  }

  private static @NotNull Runnable applyRunnable(final @NotNull Project project, final @NotNull Computable<UsageInfo[]> computable) {
    return () -> {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(
        JavaBundle.message("action.description.infer.nullity.annotations"));
      try {
        ReadAction.run(() -> {
          final UsageInfo[] infos = computable.compute();
          if (infos.length > 0) {
            var command = new Runnable() {
              private int myCount;

              @Override
              public void run() {
                final Set<VirtualFile> files =
                  StreamEx.of(infos).map(UsageInfo::getElement).nonNull()
                    .map(PsiElement::getContainingFile).nonNull()
                    .map(PsiFile::getVirtualFile).nonNull()
                    .toCollection(LinkedHashSet::new);
                if (!FileModificationService.getInstance().prepareVirtualFilesForWrite(project, files)) return;

                final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, JavaBundle
                  .message("action.title.infer.nullity.annotations"));
                progressTask.setMinIterationTime(200);
                AnnotateTask task = new AnnotateTask(project, progressTask, infos);
                progressTask.setTask(task);
                ProgressManager.getInstance().run(progressTask);
                myCount = task.getAddedCount();
              }
            };
            CommandProcessor.getInstance()
              .executeCommand(project, command, JavaBundle.message("action.title.infer.nullity.annotations"), null);
            if (command.myCount == 0) {
              NullityInferrer.nothingFoundMessage(project);
            } else {
              getNotificationGroup().createNotification(JavaBundle.message("notification.content.added.annotations", command.myCount),
                                                        NotificationType.INFORMATION)
                .notify(project);
            }
          }
          else {
            NullityInferrer.nothingFoundMessage(project);
          }
        });
      }
      finally {
        action.finish();
      }
    };
  }

  private static NotificationGroup getNotificationGroup() {
    return NotificationGroupManager.getInstance()
      .getNotificationGroup("Infer Nullity");
  }

  protected void restartAnalysis(final @NotNull Project project, final @NotNull AnalysisScope scope) {
    AppUIExecutor.onUiThread().inSmartMode(project).execute(() -> analyze(project, scope));
  }

  private void showUsageView(@NotNull Project project, final UsageInfo @NotNull [] usageInfos, @NotNull AnalysisScope scope) {
    final UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    final Ref<Usage[]> convertUsagesRef = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ApplicationManager.getApplication().runReadAction(() -> convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos))),
      JavaBundle.message("progress.title.preprocess.usages"), true, project)) {
      return;
    }

    if (convertUsagesRef.isNull()) return;
    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText(JavaBundle.message("tab.title.infer.nullity.preview"));
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    final UsageView usageView =
      UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope));

    final Runnable refactoringRunnable = applyRunnable(project, () -> {
      final Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
      return infos.toArray(UsageInfo.EMPTY_ARRAY);
    });

    String canNotMakeString = """
      Cannot perform operation.
      There were changes in code after usages have been found.
      Please perform operation search again.""";

    usageView.addPerformOperationAction(refactoringRunnable, JavaBundle.message("action.title.infer.nullity.annotations"), canNotMakeString,
                                        JavaBundle.message("action.title.infer.nullity.annotations"), false);
  }

  private @NotNull Factory<UsageSearcher> rerunFactory(final @NotNull Project project, final @NotNull AnalysisScope scope) {
    return () -> new UsageInfoSearcherAdapter() {
      @Override
      protected UsageInfo @NotNull [] findUsages() {
        return ObjectUtils.notNull(InferNullityAnnotationsAction.this.findUsages(project, scope, scope.getFileCount()),
                                   UsageInfo.EMPTY_ARRAY);
      }

      @Override
      public void generate(@NotNull Processor<? super Usage> processor) {
        processUsages(processor, project);
      }
    };
  }

  private void dispose() {
    myUi = null;
  }

  @Override
  protected @Nullable JComponent getAdditionalActionSettings(@NotNull Project project, BaseAnalysisActionDialog dialog) {
    myUi = new InferNullityAdditionalUi();
    myUi.getCheckBox().setSelected(PropertiesComponent.getInstance().getBoolean(ANNOTATE_LOCAL_VARIABLES));
    return myUi.getPanel();
  }

  /**
   * @param project current project
   * @return true if it's allowed to suggest annotation dependency for this project
   */
  public static boolean maySuggestAnnotationDependency(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getBoolean(SUGGEST_ANNOTATION_DEPENDENCY, true);
  }
}
