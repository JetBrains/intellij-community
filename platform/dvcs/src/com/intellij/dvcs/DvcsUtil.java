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
package com.intellij.dvcs;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogProvider;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class DvcsUtil {

  public static void installStatusBarWidget(@NotNull Project project, @NotNull StatusBarWidget widget) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.addWidget(widget, "after " + (SystemInfo.isMac ? "Encoding" : "InsertOverwrite"), project);
    }
  }

  public static void removeStatusBarWidget(@NotNull Project project, @NotNull StatusBarWidget widget) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.removeWidget(widget.ID());
    }
  }

  @NotNull
  public static String getShortRepositoryName(@NotNull Project project, @NotNull VirtualFile root) {
    VirtualFile projectDir = project.getBaseDir();

    String repositoryPath = root.getPresentableUrl();
    if (projectDir != null) {
      String relativePath = VfsUtilCore.getRelativePath(root, projectDir, File.separatorChar);
      if (relativePath != null) {
        repositoryPath = relativePath;
      }
    }

    return repositoryPath.isEmpty() ? root.getName() : repositoryPath;
  }

  @NotNull
  public static String getShortRepositoryName(@NotNull Repository repository) {
    return getShortRepositoryName(repository.getProject(), repository.getRoot());
  }

  @NotNull
  public static String getShortNames(@NotNull Collection<? extends Repository> repositories) {
    return StringUtil.join(repositories, new Function<Repository, String>() {
      @Override
      public String fun(Repository repository) {
        return getShortRepositoryName(repository);
      }
    }, ", ");
  }

  @NotNull
  public static String joinRootsPaths(@NotNull Collection<VirtualFile> roots) {
    return StringUtil.join(roots, new Function<VirtualFile, String>() {
      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getPresentableUrl();
      }
    }, ", ");
  }

  public static boolean anyRepositoryIsFresh(Collection<? extends Repository> repositories) {
    for (Repository repository : repositories) {
      if (repository.isFresh()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Report a warning that the given root has no associated Repositories.
   */
  public static void noVcsRepositoryForRoot(@NotNull Logger log,
                                            @NotNull VirtualFile root,
                                            @NotNull Project project,
                                            @NotNull RepositoryManager repositoryManager,
                                            @Nullable AbstractVcs vcs) {
    if (vcs == null) {
      return;
    }
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    List<VirtualFile> roots = Arrays.asList(vcsManager.getRootsUnderVcs(vcs));
    log.warn(String.format("Repository not found for root: %s. All roots: %s, all repositories: %s", root, roots,
                           repositoryManager.getRepositories()));
  }

  /**
   * Checks if there are hg roots in the VCS log.
   */
  public static boolean logHasRootForVcs(@NotNull VcsLog log, @Nullable final VcsKey vcsKey) {
    return ContainerUtil.find(log.getLogProviders(), new Condition<VcsLogProvider>() {
      @Override
      public boolean value(VcsLogProvider logProvider) {
        return logProvider.getSupportedVcs().equals(vcsKey);
      }
    }) != null;
  }

  @Nullable
  public static String joinMessagesOrNull(@NotNull Collection<String> messages) {
    String joined = StringUtil.join(messages, "\n");
    return StringUtil.isEmptyOrSpaces(joined) ? null : joined;
  }

  /**
   * Returns the currently selected file, based on which VcsBranch or StatusBar components will identify the current repository root.
   */
  @Nullable
  public static VirtualFile getSelectedFile(@NotNull Project project) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    final FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(project, statusBar);
    VirtualFile result = null;
    if (fileEditor != null) {
      if (fileEditor instanceof TextEditor) {
        Document document = ((TextEditor)fileEditor).getEditor().getDocument();
        result = FileDocumentManager.getInstance().getFile(document);
      }
      else if (fileEditor instanceof ImageFileEditor) {
        result = ((ImageFileEditor)fileEditor).getImageEditor().getFile();
      }
    }

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      if (manager != null) {
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          result = FileDocumentManager.getInstance().getFile(editor.getDocument());
        }
      }
    }
    return result;
  }

}
