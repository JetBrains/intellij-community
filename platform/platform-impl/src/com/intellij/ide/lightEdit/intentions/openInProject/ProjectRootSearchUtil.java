// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.intentions.openInProject;

import com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil;
import com.intellij.ide.lightEdit.LightEditUtil;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.ProjectStatus.Existing;
import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.ProjectStatus.New;

final class ProjectRootSearchUtil {
  private final static ProjectRootFinder[] ROOT_FINDERS = {
    new IntellijProjectRootFinder(),
    new GradleProjectRootFinder(),
    new GitProjectRootFinder(),
    new SimpleParentRootFinder()
  };

  private ProjectRootSearchUtil() {
  }

  static @Nullable VirtualFile findProjectRoot(@NotNull VirtualFile sourceFile) {
    Ref<VirtualFile> result = Ref.create();
    Ref<Boolean> requiresConfirmation = Ref.create(false);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> {
        for (ProjectRootFinder finder : ROOT_FINDERS) {
          VirtualFile root = finder.findProjectRoot(sourceFile);
          if (root != null) {
            result.set(root);
            requiresConfirmation.set(finder.requiresConfirmation());
            break;
          }
        }
      },
      ApplicationBundle.message("light.edit.open.in.project.progress.message"),
      true,
      LightEditUtil.getProject()
    );
    if (requiresConfirmation.get()) {
      final VirtualFile newProjectRoot = confirmOrChooseProjectDir(result.get());
      if (newProjectRoot != null) {
        LightEditFeatureUsagesUtil.logOpenFileInProject(New);
      }
      result.set(newProjectRoot);
    }
    else {
      LightEditFeatureUsagesUtil.logOpenFileInProject(Existing);
    }
    return result.get();
  }

  private static @Nullable VirtualFile confirmOrChooseProjectDir(@Nullable VirtualFile suggestedRoot) {
    if (suggestedRoot != null) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      descriptor.setTitle(ApplicationBundle.message("light.edit.open.in.project.dialog.title"));
      return FileChooser.chooseFile(descriptor, LightEditUtil.getProject(), suggestedRoot);
    }
    return null;
  }
}
