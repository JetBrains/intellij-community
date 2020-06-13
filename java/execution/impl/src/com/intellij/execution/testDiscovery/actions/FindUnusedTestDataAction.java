// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testDiscovery.IntellijTestDiscoveryProducer;
import com.intellij.find.FindUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.vfs.LocalFileSystem.PROTOCOL_PREFIX;

public class FindUnusedTestDataAction extends DumbAwareAction {
  private final static Logger LOG = Logger.getInstance(FindUnusedTestDataAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] roots = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Project project = e.getProject();
    e.getPresentation().setEnabled(roots != null && roots.length > 0 && project != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VirtualFile[] roots = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    Project project = e.getProject();
    assert project != null;
    assert roots != null && roots.length > 0;

    VirtualFile projectBasePath = ShowAffectedTestsAction.getBasePathAsVirtualFile(project);
    if (projectBasePath == null) return;

    Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      Set<String> paths = JBIterable.of(roots).flatMap(f -> VfsUtil.collectChildrenRecursively(f))
        .map(f -> VcsFileUtil.getRelativeFilePath(f, projectBasePath))
        .filter(Objects::nonNull)
        .map(p -> "/" + p)
        .toSet();

      try {
        List<String> filesWithoutTests = new IntellijTestDiscoveryProducer().getFilesWithoutTests(project, paths);

        if (!filesWithoutTests.isEmpty()) {
          VirtualFileManager vfm = VirtualFileManager.getInstance();
          PsiManager psiManager = PsiManager.getInstance(project);
          String basePath = projectBasePath.getPath();
          application.runReadAction(() -> {
            PsiFile[] files = JBIterable.of(filesWithoutTests)
                .flatten(FunctionUtil.id())
                .map(f -> vfm.refreshAndFindFileByUrl(PROTOCOL_PREFIX + basePath + f))
                .map(psiManager::findFile)
                .filter(Objects::nonNull).toSet().toArray(PsiFile.EMPTY_ARRAY);

            if (files.length == 0) {
              nothingToDo();
            }
            else {
              application.invokeLater(
                () -> FindUtil.showInUsageView(
                  null, files,
                  file -> new UsageInfo2UsageAdapter(new UsageInfo(file)),
                  "Unused Test Data",
                  p -> {
                    p.setCodeUsages(false);
                    p.setUsagesWord(count -> ExecutionBundle.message("label.usages.word.file", count));
                  }, project));
            }
          });
        }
        else {
          nothingToDo();
        }
      }
      catch (IOException io) {
        LOG.warn(io);
      }
    });
  }

  private static void nothingToDo() {
    Notifications.Bus.notify(new Notification(FindUnusedTestDataAction.class.getName(),
                                              ExecutionBundle.message("well.done"),
                                              ExecutionBundle.message("every.file.is.used"),
                                              NotificationType.INFORMATION));
  }
}
