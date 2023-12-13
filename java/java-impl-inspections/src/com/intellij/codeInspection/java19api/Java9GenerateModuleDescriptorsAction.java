// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInspection.java19api.DescriptorsGenerator.ModuleFiles;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class Java9GenerateModuleDescriptorsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(Java9GenerateModuleDescriptorsAction.class);
  private static final String CLASS_FILE_PATTERN = "glob:*" + CommonClassNames.CLASS_FILE_EXTENSION;

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null && !DumbService.isDumb(project) && isModularJdkAvailable());
  }

  private static boolean isModularJdkAvailable() {
    return ContainerUtil.exists(ProjectJdkTable.getInstance().getAllJdks(), sdk -> JavaSdkUtil.isJdkAtLeast(sdk, JavaSdkVersion.JDK_1_9));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    CompilerManager compilerManager = CompilerManager.getInstance(project);
    CompileScope scope = compilerManager.createProjectCompileScope(project);
    if (!compilerManager.isUpToDate(scope)) {
      int result = Messages.showYesNoCancelDialog(
        project, JavaRefactoringBundle.message("generate.module.descriptors.rebuild.message"), getTitle(), null);
      switch (result) {
        case Messages.YES:
          compilerManager.compile(scope, (aborted, errors, warnings, compileContext) -> {
            if (!aborted && errors == 0) generate(project);
          });
          return;
        case Messages.CANCEL:
          return;
      }
    }
    generate(project);
  }

  private static void generate(@NotNull Project project) {
    DumbService.getInstance(project).smartInvokeLater(() -> generateWhenSmart(project));
  }

  private static void generateWhenSmart(@NotNull Project project) {
    LOG.assertTrue(!DumbService.isDumb(project), "Module name index should be ready");
    final UniqueModuleNames names = new UniqueModuleNames(project);
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, getTitle(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final List<ModuleFiles> moduleFiles = collectClassFiles(project);
          if (ContainerUtil.exists(moduleFiles, module -> !module.files().isEmpty())) {
            new DescriptorsGenerator(project, names, LOG).generate(moduleFiles, indicator);
          }
          else {
            CommonRefactoringUtil.showErrorHint(project, null,
                                                JavaRefactoringBundle.message("generate.module.descriptors.build.required.message"),
                                                getTitle(), null);
          }
        }
      });
  }

  @NotNull
  private static List<ModuleFiles> collectClassFiles(@NotNull Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(true);
    indicator.setText(JavaRefactoringBundle.message("generate.module.descriptors.scanning.message"));

    List<ModuleFiles> moduleFiles = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!mayContainModuleInfo(module)) continue;
      try {
        String production = CompilerPaths.getModuleOutputPath(module, false);
        Path productionRoot = production != null ? Paths.get(production) : null;
        moduleFiles.add(new ModuleFiles(module, collectClassFiles(productionRoot)));
      }
      catch (IOException e) {
        CommonRefactoringUtil.showErrorHint(
          project, null,
          JavaRefactoringBundle.message("generate.module.descriptors.io.exceptions.message", module.getName()),
          getTitle(), null);
        return Collections.emptyList();
      }
    }
    if (moduleFiles.isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, null,
                                          JavaRefactoringBundle.message("generate.module.descriptors.no.suitable.modules.message"),
                                          getTitle(), null);
    }
    return moduleFiles;
  }

  private static boolean mayContainModuleInfo(@NotNull final Module module) {
    return ReadAction.compute(() -> LanguageLevelUtil.getEffectiveLanguageLevel(module).isAtLeast(LanguageLevel.JDK_1_9));
  }

  @NotNull
  private static List<Path> collectClassFiles(@Nullable Path file) throws IOException {
    if (file == null) return Collections.emptyList();
    try (Stream<Path> stream = Files.walk(file)) {
      final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(CLASS_FILE_PATTERN);
      return stream.filter(path -> matcher.matches(path.getFileName())).toList();
    }
  }

  private static @NlsContexts.DialogTitle String getTitle() {
    return JavaRefactoringBundle.message("generate.module.descriptors.title");
  }
}
