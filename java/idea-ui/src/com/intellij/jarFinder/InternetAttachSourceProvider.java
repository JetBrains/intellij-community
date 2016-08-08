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
package com.intellij.jarFinder;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class InternetAttachSourceProvider extends AbstractAttachSourceProvider {
  private static final Logger LOG = Logger.getInstance(InternetAttachSourceProvider.class);
  private static final Pattern ARTIFACT_IDENTIFIER = Pattern.compile("[A-Za-z0-9\\.\\-_]+");

  @NotNull
  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, @Nullable PsiFile psiFile) {
    final VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return Collections.emptyList();

    final String jarName = jar.getNameWithoutExtension();
    int index = jarName.lastIndexOf('-');
    if (index == -1) return Collections.emptyList();

    final String version = jarName.substring(index + 1);
    final String artifactId = jarName.substring(0, index);

    if (!ARTIFACT_IDENTIFIER.matcher(version).matches() || !ARTIFACT_IDENTIFIER.matcher(artifactId).matches()) {
      return Collections.emptyList();
    }

    final Set<Library> libraries = new HashSet<>();
    for (LibraryOrderEntry orderEntry : orderEntries) {
      ContainerUtil.addIfNotNull(libraries, orderEntry.getLibrary());
    }

    if (libraries.isEmpty()) return Collections.emptyList();

    final String sourceFileName = jarName + "-sources.jar";

    for (Library library : libraries) {
      for (VirtualFile file : library.getFiles(OrderRootType.SOURCES)) {
        if (file.getPath().contains(sourceFileName)) {
          if (isRootInExistingFile(file)) {
            return Collections.emptyList(); // Sources already attached, but source-jar doesn't contain current class.
          }
        }
      }
    }

    final File libSourceDir = getLibrarySourceDir();

    final File sourceFile = new File(libSourceDir, sourceFileName);

    if (sourceFile.exists()) {
      return Collections.singleton(new LightAttachSourcesAction() {
        @Override
        public String getName() {
          return "Attach downloaded source";
        }

        @Override
        public String getBusyText() {
          return getName();
        }

        @Override
        public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
          attachSourceJar(sourceFile, libraries);
          return ActionCallback.DONE;
        }
      });
    }

    return Collections.singleton(new LightAttachSourcesAction() {
      @Override
      public String getName() {
        return "Download...";
      }

      @Override
      public String getBusyText() {
        return "Searching...";
      }

      @Override
      public ActionCallback perform(List<LibraryOrderEntry> orderEntriesContainingFile) {
        final Task task = new Task.Modal(psiFile.getProject(), "Searching source...", true) {
          @Override
          public void run(@NotNull final ProgressIndicator indicator) {
            String artifactUrl = null;

            SourceSearcher[] searchers = {new MavenCentralSourceSearcher(), new SonatypeSourceSearcher()};
            for (SourceSearcher searcher : searchers) {
              try {
                artifactUrl = searcher.findSourceJar(indicator, artifactId, version, jar);
              }
              catch (SourceSearchException e) {
                LOG.warn(e);
                showMessage("Downloading failed", e.getMessage(), NotificationType.ERROR);
                continue;
              }

              if (artifactUrl != null) break;
            }

            if (artifactUrl == null) {
              showMessage("Sources not found", "Sources for '" + jarName + ".jar' not found", NotificationType.WARNING);
              return;
            }

            if (!(libSourceDir.isDirectory() || libSourceDir.mkdirs())) {
              showMessage("Downloading failed", "Failed to create directory to store sources: " + libSourceDir, NotificationType.ERROR);
              return;
            }

            try {
              File tmpDownload = FileUtil.createTempFile(libSourceDir, "download.", ".tmp", false, false);
              HttpRequests.request(artifactUrl).saveToFile(tmpDownload, indicator);
              if (!sourceFile.exists() && !tmpDownload.renameTo(sourceFile)) {
                LOG.warn("Failed to rename file " + tmpDownload + " to " + sourceFileName);
              }
            }
            catch (IOException e) {
              LOG.warn(e);
              showMessage("Downloading failed", "Connection problem. See log for more details.", NotificationType.ERROR);
            }
          }

          @Override
          public void onSuccess() {
            attachSourceJar(sourceFile, libraries);
          }

          private void showMessage(String title, String message, NotificationType notificationType) {
            new Notification("Source searcher", title, message, notificationType).notify(getProject());
          }
        };

        task.queue();

        return ActionCallback.DONE;
      }
    });
  }

  private static boolean isRootInExistingFile(VirtualFile root) {
    if (root.getFileSystem() instanceof JarFileSystem) {
      VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(root);
      // we might be invoked outside EDT, so sync VFS refresh is impossible, so we check java.io.File existence
      if (jar == null || !VfsUtilCore.virtualToIoFile(jar).exists()) return false;
    }

    return true;
  }

  public static void attachSourceJar(@NotNull File sourceJar, @NotNull Collection<Library> libraries) {
    AccessToken accessToken = WriteAction.start();
    try {
      VirtualFile srcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceJar);
      if (srcFile == null) return;

      VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile);
      if (jarRoot == null) return;

      VirtualFile[] roots = LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(null, new VirtualFile[]{jarRoot});
      if (roots.length == 0) {
        roots = new VirtualFile[]{jarRoot};
      }

      for (Library library : libraries) {
        Library.ModifiableModel model = library.getModifiableModel();
        List<VirtualFile> alreadyExistingFiles = Arrays.asList(model.getFiles(OrderRootType.SOURCES));

        for (VirtualFile root : roots) {
          if (!alreadyExistingFiles.contains(root)) {
            model.addRoot(root, OrderRootType.SOURCES);
          }
        }
        model.commit();
      }
    }
    finally {
      accessToken.finish();
    }
  }

  public static File getLibrarySourceDir() {
    String path = System.getProperty("idea.library.source.dir");
    return path != null ? new File(path) : new File(SystemProperties.getUserHome(), ".ideaLibSources");
  }
}