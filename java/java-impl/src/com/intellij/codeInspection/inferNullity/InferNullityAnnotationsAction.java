/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.LocateLibraryDialog;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
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
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InferNullityAnnotationsAction extends BaseAnalysisAction {
  @NonNls private static final String INFER_NULLITY_ANNOTATIONS = "Infer Nullity Annotations";
  @NonNls private static final String ANNOTATE_LOCAL_VARIABLES = "annotate.local.variables";
  private JCheckBox myAnnotateLocalVariablesCb;

  public InferNullityAnnotationsAction() {
    super("Infer Nullity", INFER_NULLITY_ANNOTATIONS);
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {

    PropertiesComponent.getInstance().setValue(ANNOTATE_LOCAL_VARIABLES, String.valueOf(myAnnotateLocalVariablesCb.isSelected()));

    final ProgressManager progressManager = ProgressManager.getInstance();
    final int totalFiles = scope.getFileCount();

    final Set<Module> modulesWithoutAnnotations = new HashSet<Module>();
    final Set<Module> modulesWithLL = new HashSet<Module>();
    if (!progressManager.runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {

        scope.accept(new PsiElementVisitor() {
          private int myFileCount = 0;
          final private Set<Module> processed = new HashSet<Module>();

          @Override
          public void visitFile(PsiFile file) {
            myFileCount++;
            final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            if (progressIndicator != null) {
              final VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                progressIndicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
              }
              progressIndicator.setFraction(((double)myFileCount) / totalFiles);
            }
            final Module module = ModuleUtilCore.findModuleForPsiElement(file);
            if (module != null && !processed.contains(module)) {
              processed.add(module);
              if (JavaPsiFacade.getInstance(project)
                    .findClass(NullableNotNullManager.getInstance(project).getDefaultNullable(),
                               GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) == null) {
                modulesWithoutAnnotations.add(module);
              }
              if (PsiUtil.getLanguageLevel(file).compareTo(LanguageLevel.JDK_1_5) < 0) {
                modulesWithLL.add(module);
              }
            }
          }
        });
      }
    }, "Check applicability...", true, project)) {
      return;
    }
    if (!modulesWithLL.isEmpty()) {
      Messages.showErrorDialog(project, "Infer Nullity Annotations requires the project language level be set to 1.5 or greater.",
                               INFER_NULLITY_ANNOTATIONS);
      return;
    }
    if (!modulesWithoutAnnotations.isEmpty()) {
      final Library annotationsLib =
        LibraryUtil.findLibraryByClass(NullableNotNullManager.getInstance(project).getDefaultNullable(), project);
      if (annotationsLib != null) {
        String message = "Module" + (modulesWithoutAnnotations.size() == 1 ? " " : "s ");
        message += StringUtil.join(modulesWithoutAnnotations, new Function<Module, String>() {
          @Override
          public String fun(Module module) {
            return module.getName();
          }
        }, ", ");
        message += (modulesWithoutAnnotations.size() == 1 ? " doesn't" : " don't");
        message += " refer to the existing '" +
                   annotationsLib.getName() +
                   "' library with IDEA nullity annotations. Would you like to add the dependenc";
        message += (modulesWithoutAnnotations.size() == 1 ? "y" : "ies") + " now?";
        if (Messages.showOkCancelDialog(project, message, INFER_NULLITY_ANNOTATIONS, Messages.getErrorIcon()) ==
            Messages.OK) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              for (Module module : modulesWithoutAnnotations) {
                ModuleRootModificationUtil.addDependency(module, annotationsLib);
              }
            }
          });
          restartAnalysis(project, scope);
        }
      }
      else if (Messages.showOkCancelDialog(project, "Infer Nullity Annotations requires that the nullity annotations" +
                                                    " be available in all your project sources.\n\nYou will need to add annotations.jar as a library. " +
                                                    "It is possible to configure custom jar in e.g. Constant Conditions & Exceptions inspection or use JetBrains annotations available in installation. " +
                                                    " IntelliJ IDEA nullity annotations are freely usable and redistributable under the Apache 2.0 license. Would you like to do it now?",
                                           INFER_NULLITY_ANNOTATIONS, Messages.getErrorIcon()) == Messages.OK) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            final LocateLibraryDialog dialog =
              new LocateLibraryDialog(modulesWithoutAnnotations.iterator().next(), PathManager.getLibPath(), "annotations.jar",
                                      QuickFixBundle.message("add.library.annotations.description"));
            dialog.show();
            if (dialog.isOK()) {
              final String path = dialog.getResultingLibraryPath();
              new WriteCommandAction(project) {
                @Override
                protected void run(final Result result) throws Throwable {
                  for (Module module : modulesWithoutAnnotations) {
                    OrderEntryFix.addBundledJarToRoots(project, null, module, null, AnnotationUtil.NOT_NULL, path);
                  }
                }
              }.execute();
              restartAnalysis(project, scope);
            }
          }
        });
      }
      return;
    }
    if (scope.checkScopeWritable(project)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final UsageInfo[] usageInfos = findUsages(project, scope);
    if (usageInfos == null) return;

    if (usageInfos.length < 5) {
      SwingUtilities.invokeLater(applyRunnable(project, new Computable<UsageInfo[]>() {
        @Override
        public UsageInfo[] compute() {
          return usageInfos;
        }
      }));
    }
    else {
      showUsageView(project, usageInfos, scope);
    }
  }

  private UsageInfo[] findUsages(final Project project,
                               final AnalysisScope scope) {
    final NullityInferrer inferrer = new NullityInferrer(myAnnotateLocalVariablesCb.isSelected(), project);
    final PsiManager psiManager = PsiManager.getInstance(project);
    final Runnable searchForUsages = new Runnable() {
      @Override
      public void run() {
        final int totalFiles = scope.getFileCount();
        scope.accept(new PsiElementVisitor() {
          int myFileCount = 0;

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
              progressIndicator.setFraction(((double)myFileCount) / totalFiles);
            }
            if (file instanceof PsiJavaFile) {
              inferrer.collect(file);
            }
          }
        });
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(searchForUsages, INFER_NULLITY_ANNOTATIONS, true, project)) {
        return null;
      }
    } else {
      searchForUsages.run();
    }

    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    inferrer.collect(usages);
    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private static Runnable applyRunnable(final Project project, final Computable<UsageInfo[]> computable) {
    return new Runnable() {
      @Override
      public void run() {
        final LocalHistoryAction action = LocalHistory.getInstance().startAction(INFER_NULLITY_ANNOTATIONS);
        try {
          new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
            @Override
            protected void run(Result result) throws Throwable {
              final UsageInfo[] infos = computable.compute();
              if (infos.length > 0) {
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
      }
    };
  }

  private void restartAnalysis(final Project project, final AnalysisScope scope) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        analyze(project, scope);
      }
    });
  }


  private void showUsageView(final Project project, final UsageInfo[] usageInfos, AnalysisScope scope) {
    final UsageTarget[] targets = UsageTarget.EMPTY_ARRAY;
    final Ref<Usage[]> convertUsagesRef = new Ref<Usage[]>();
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos));
          }
        });
      }
    }, "Preprocess usages", true, project)) return;

    if (convertUsagesRef.isNull()) return;
    final Usage[] usages = convertUsagesRef.get();

    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTabText("Infer Nullity Preview");
    presentation.setShowReadOnlyStatusAsRed(true);
    presentation.setShowCancelButton(true);
    presentation.setUsagesString(RefactoringBundle.message("usageView.usagesText"));

    final UsageView usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope));

    final Runnable refactoringRunnable = applyRunnable(project, new Computable<UsageInfo[]>() {
      @Override
      public UsageInfo[] compute() {
        final Set<UsageInfo> infos = UsageViewUtil.getNotExcludedUsageInfos(usageView);
        return infos.toArray(new UsageInfo[infos.size()]);
      }
    });

    String canNotMakeString = "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again.";

    usageView.addPerformOperationAction(refactoringRunnable, INFER_NULLITY_ANNOTATIONS, canNotMakeString, INFER_NULLITY_ANNOTATIONS, false);
  }

  private Factory<UsageSearcher> rerunFactory(final Project project, final AnalysisScope scope) {
    return new Factory<UsageSearcher>() {
      @Override
      public UsageSearcher create() {
        return new UsageInfoSearcherAdapter() {
          @Override
          protected UsageInfo[] findUsages() {
            return InferNullityAnnotationsAction.this.findUsages(project, scope);
          }

          @Override
          public void generate(@NotNull Processor<Usage> processor) {
            processUsages(processor, project);
          }
        };
      }
    };
  }

  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(new TitledSeparator());
    myAnnotateLocalVariablesCb = new JCheckBox("Annotate local variables", PropertiesComponent.getInstance().getBoolean(ANNOTATE_LOCAL_VARIABLES, false));
    panel.add(myAnnotateLocalVariablesCb);
    return panel;
  }

  @Override
  protected void canceled() {
    super.canceled();
    myAnnotateLocalVariablesCb = null;
  }
}
