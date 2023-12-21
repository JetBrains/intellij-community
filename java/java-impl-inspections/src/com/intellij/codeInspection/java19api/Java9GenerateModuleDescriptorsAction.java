// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19api;

import com.intellij.codeInspection.java19api.DescriptorsGenerator.ModuleFiles;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
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
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.platform.backend.workspace.WorkspaceModel;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
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
    ProgressManager.getInstance().run(
      new Task.Backgroundable(project, getTitle(), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          final List<ModuleFiles> moduleFiles = collectClassFiles(project);
          if (ContainerUtil.exists(moduleFiles, module -> !module.files().isEmpty())) {
            new DescriptorsGenerator(project, new UniqueModuleNames(project), LOG).generate(moduleFiles, indicator);
          }
          else {
            NotificationGroupManager.getInstance().getNotificationGroup("Failed to generate module descriptors")
              .createNotification(getTitle(), JavaRefactoringBundle.message("generate.module.descriptors.build.required.message"), NotificationType.ERROR)
              .setImportant(true)
              .notify(project);
          }
        }
      });
  }

  @NotNull
  private static List<ModuleFiles> collectClassFiles(@NotNull Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    indicator.setIndeterminate(true);
    indicator.setText(JavaRefactoringBundle.message("generate.module.descriptors.scanning.message"));

    Map<String, JavaModuleSettingsEntity> moduleSettingsByName = new HashMap<>();
    final Iterator<JavaModuleSettingsEntity> iterator = WorkspaceModel.getInstance(project).getCurrentSnapshot()
      .entities(JavaModuleSettingsEntity.class).iterator();
    while (iterator.hasNext()) {
      final JavaModuleSettingsEntity moduleSettings = iterator.next();
      moduleSettingsByName.put(moduleSettings.getModule().getName(), moduleSettings);
    }

    List<ModuleFiles> moduleFiles = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!mayContainModuleInfo(module)) continue;
      try {
        Path production = null;
        final JavaModuleSettingsEntity moduleSettings = moduleSettingsByName.get(module.getName());
        if (moduleSettings != null) {
          final VirtualFileUrl compilerOutput = moduleSettings.getCompilerOutput();
          if (compilerOutput != null) {
            final String url = VirtualFileManager.extractPath(compilerOutput.getUrl());
            final Path path = Paths.get(url);
            production = Files.exists(path) ? path : null;
          }
        }

        if (production == null) {
          final String url = CompilerPaths.getModuleOutputPath(module, false);
          if (url != null) {
            final Path path = Paths.get(url);
            production = Files.exists(path) ? path : null;
          }
        }

        moduleFiles.add(new ModuleFiles(module, collectClassFiles(production)));
      }
      catch (IOException e) {
        NotificationGroupManager.getInstance().getNotificationGroup("Failed to generate module descriptors")
          .createNotification(getTitle(), JavaRefactoringBundle.message("generate.module.descriptors.io.exceptions.message", module.getName()), NotificationType.ERROR)
          .setImportant(true)
          .notify(project);
        return Collections.emptyList();
      }
    }
    if (moduleFiles.isEmpty()) {
      NotificationGroupManager.getInstance().getNotificationGroup("Failed to generate module descriptors")
        .createNotification(getTitle(), JavaRefactoringBundle.message("generate.module.descriptors.no.suitable.modules.message"), NotificationType.WARNING)
        .setImportant(true)
        .notify(project);
    }
    return moduleFiles;
  }

  private static boolean mayContainModuleInfo(@NotNull final Module module) {
    return ReadAction.compute(() -> LanguageLevelUtil.getEffectiveLanguageLevel(module).isAtLeast(LanguageLevel.JDK_1_9));
  }

  @NotNull
  private static List<Path> collectClassFiles(@Nullable Path file) throws IOException {
    if (file == null || !Files.exists(file)) return Collections.emptyList();
    try (Stream<Path> stream = Files.walk(file)) {
      final PathMatcher matcher = FileSystems.getDefault().getPathMatcher(CLASS_FILE_PATTERN);
      return stream.filter(path -> matcher.matches(path.getFileName())).toList();
    }
  }

  private static @NlsContexts.DialogTitle String getTitle() {
    return JavaRefactoringBundle.message("generate.module.descriptors.title");
  }
}
