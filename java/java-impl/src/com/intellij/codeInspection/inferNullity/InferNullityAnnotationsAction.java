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
package com.intellij.codeInspection.inferNullity;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.impl.quickfix.JetBrainsAnnotationsExternalLibraryResolver;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionId;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.TitledSeparator;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class InferNullityAnnotationsAction extends BaseAnalysisAction {
  @NonNls private static final String INFER_NULLITY_ANNOTATIONS = "Infer Nullity Annotations";
  @NonNls private static final String ANNOTATE_LOCAL_VARIABLES = "annotate.local.variables";
  private JCheckBox myAnnotateLocalVariablesCb;

  public InferNullityAnnotationsAction() {
    super("Infer Nullity", INFER_NULLITY_ANNOTATIONS);
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    PropertiesComponent.getInstance().setValue(ANNOTATE_LOCAL_VARIABLES, myAnnotateLocalVariablesCb.isSelected());

    final ProgressManager progressManager = ProgressManager.getInstance();
    final Set<Module> modulesWithoutAnnotations = new HashSet<>();
    final Set<Module> modulesWithLL = new HashSet<>();
    final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    final String defaultNullable = NullableNotNullManager.getInstance(project).getDefaultNullable();
    final int[] fileCount = new int[] {0};
    if (!progressManager.runProcessWithProgressSynchronously(() -> scope.accept(new PsiElementVisitor() {
      final private Set<Module> processed = new HashSet<>();

      @Override
      public void visitFile(PsiFile file) {
        fileCount[0]++;
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
          final VirtualFile virtualFile = file.getVirtualFile();
          if (virtualFile != null) {
            progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          }
          progressIndicator.setText(AnalysisScopeBundle.message("scanning.scope.progress.title"));
        }
        if (!(file instanceof PsiJavaFile)) return;
        final Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module != null && processed.add(module)) {
          if (PsiUtil.getLanguageLevel(file).compareTo(LanguageLevel.JDK_1_5) < 0) {
            modulesWithLL.add(module);
          }
          else if (javaPsiFacade.findClass(defaultNullable, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) == null) {
            modulesWithoutAnnotations.add(module);
          }
        }
      }
    }), "Check Applicability...", true, project)) {
      return;
    }
    if (!modulesWithLL.isEmpty()) {
      Messages.showErrorDialog(project, "Infer Nullity Annotations requires the project language level be set to 1.5 or greater.",
                               INFER_NULLITY_ANNOTATIONS);
      return;
    }
    if (!modulesWithoutAnnotations.isEmpty()) {
      if (addAnnotationsDependency(project, modulesWithoutAnnotations, defaultNullable, INFER_NULLITY_ANNOTATIONS)) {
        restartAnalysis(project, scope);
      }
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final UsageInfo[] usageInfos = findUsages(project, scope, fileCount[0]);
    if (usageInfos == null) return;

    processUsages(project, scope, usageInfos);
  }

  protected void processUsages(@NotNull Project project, @NotNull AnalysisScope scope, @NotNull UsageInfo[] usageInfos) {
    if (usageInfos.length < 5) {
      applyRunnable(project, () -> usageInfos).run();
    }
    else {
      showUsageView(project, usageInfos, scope);
    }
  }

  public static boolean addAnnotationsDependency(@NotNull final Project project,
                                                 @NotNull final Set<Module> modulesWithoutAnnotations,
                                                 @NotNull String annoFQN, final String title) {
    final Library annotationsLib = LibraryUtil.findLibraryByClass(annoFQN, project);
    if (annotationsLib != null) {
      String message = "Module" + (modulesWithoutAnnotations.size() == 1 ? " " : "s ");
      message += StringUtil.join(modulesWithoutAnnotations, Module::getName, ", ");
      message += (modulesWithoutAnnotations.size() == 1 ? " doesn't" : " don't");
      message += " refer to the existing '" +
                 annotationsLib.getName() +
                 "' library with IntelliJ IDEA nullity annotations. Would you like to add the dependenc";
      message += (modulesWithoutAnnotations.size() == 1 ? "y" : "ies") + " now?";
      if (Messages.showOkCancelDialog(project, message, title, Messages.getErrorIcon()) == Messages.OK) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          for (Module module : modulesWithoutAnnotations) {
            ModuleRootModificationUtil.addDependency(module, annotationsLib);
          }
        });
        return true;
      }
      return false;
    }
    
    if (Messages.showOkCancelDialog(project, "It is required that JetBrains annotations" +
                                             " be available in all your project sources.\n\nYou will need to add annotations.jar as a library. " +
                                             "It is possible to configure custom JAR\nin e.g. Constant Conditions & Exceptions inspection or use JetBrains annotations available in installation. " +
                                             "\nIntelliJ IDEA nullity annotations are freely usable and redistributable under the Apache 2.0 license.\nWould you like to do it now?",
                                    title, Messages.getErrorIcon()) == Messages.OK) {
      Module firstModule = modulesWithoutAnnotations.iterator().next();
      JavaProjectModelModificationService.getInstance(project).addDependency(modulesWithoutAnnotations, JetBrainsAnnotationsExternalLibraryResolver.getAnnotationsLibraryDescriptor(firstModule),
                                                                             DependencyScope.COMPILE);
      return true;
    }
    return false;
  }

  @Nullable
  protected UsageInfo[] findUsages(@NotNull final Project project,
                                 @NotNull final AnalysisScope scope,
                                 final int fileCount) {
    final NullityInferrer inferrer = new NullityInferrer(isAnnotateLocalVariables(), project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final Runnable searchForUsages = () -> scope.accept(new PsiElementVisitor() {
      int myFileCount;

      @Override
      public void visitFile(final PsiFile file) {
        myFileCount++;
        final VirtualFile virtualFile = file.getVirtualFile();
        final FileViewProvider viewProvider = psiManager.findViewProvider(virtualFile);
        final Document document = viewProvider == null ? null : viewProvider.getDocument();
        if (document == null || virtualFile.getFileType().isBinary()) return; //do not inspect binary files
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
          progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
          progressIndicator.setFraction(((double)myFileCount) / fileCount);
        }
        if (file instanceof PsiJavaFile) {
          inferrer.collect(file);
        }
      }
    });
    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(searchForUsages, INFER_NULLITY_ANNOTATIONS, true, project)) {
        return null;
      }
    } else {
      searchForUsages.run();
    }

    final List<UsageInfo> usages = new ArrayList<>();
    inferrer.collect(usages);
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected boolean isAnnotateLocalVariables() {
    return myAnnotateLocalVariablesCb.isSelected();
  }

  private static Runnable applyRunnable(final Project project, final Computable<UsageInfo[]> computable) {
    return () -> {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_NULLITY_ANNOTATIONS);
      try {
        new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
          @Override
          protected void run(@NotNull Result result) throws Throwable {
            final UsageInfo[] infos = computable.compute();
            if (infos.length > 0) {

              final Set<PsiElement> elements = new LinkedHashSet<>();
              for (UsageInfo info : infos) {
                final PsiElement element = info.getElement();
                if (element != null) {
                  ContainerUtil.addIfNotNull(elements, element.getContainingFile());
                }
              }
              if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return;

              final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, INFER_NULLITY_ANNOTATIONS, false);
              progressTask.setMinIterationTime(200);
              progressTask.setTask(new AnnotateTask(project, progressTask, infos));
              ProgressManager.getInstance().run(progressTask);
            } else {
              NullityInferrer.nothingFoundMessage(project);
            }
          }
        }.execute();
      }
      finally {
        action.finish();
      }
    };
  }

  protected void restartAnalysis(final Project project, final AnalysisScope scope) {
    TransactionGuard guard = TransactionGuard.getInstance();
    TransactionId id = guard.getContextTransaction();
    DumbService.getInstance(project).smartInvokeLater(
      () -> TransactionGuard.getInstance().submitTransaction(project, id, () -> {
        if (DumbService.isDumb(project)) {
          restartAnalysis(project, scope);
        } else {
          analyze(project, scope);
        }
      }));
  }

  private void showUsageView(@NotNull Project project, final UsageInfo[] usageInfos, @NotNull AnalysisScope scope) {
    final UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    final Ref<Usage[]> convertUsagesRef = new Ref<>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos))), "Preprocess Usages", true, project)) return;

    if (convertUsagesRef.isNull()) return;
    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    final UsageView usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope));

    final Runnable refactoringRunnable = applyRunnable(project, () -> {
      final Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
      return infos.toArray(new UsageInfo[infos.size()]);
    });

    String canNotMakeString = "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_NULLITY_ANNOTATIONS, canNotMakeString, INFER_NULLITY_ANNOTATIONS, false);
  }

  @NotNull
  private Factory<UsageSearcher> rerunFactory(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    return () -> new UsageInfoSearcherAdapter() {
      @Override
      protected UsageInfo[] findUsages() {
        return InferNullityAnnotationsAction.this.findUsages(project, scope, scope.getFileCount());
      }

      @Override
      public void generate(@NotNull Processor<Usage> processor) {
        processUsages(processor, project);
      }
    };
  }

  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(new TitledSeparator());
    myAnnotateLocalVariablesCb = new JCheckBox("Annotate local variables", PropertiesComponent.getInstance().getBoolean(ANNOTATE_LOCAL_VARIABLES));
    panel.add(myAnnotateLocalVariablesCb);
    return panel;
  }

  @Override
  protected void canceled() {
    super.canceled();
    myAnnotateLocalVariablesCb = null;
  }
}
