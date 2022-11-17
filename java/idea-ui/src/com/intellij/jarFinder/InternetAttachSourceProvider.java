// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarFinder;

import com.intellij.ide.JavaUiBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibrarySourceRootDetectorUtil;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public final class InternetAttachSourceProvider extends AbstractAttachSourceProvider {

  private static final Logger LOG = Logger.getInstance(InternetAttachSourceProvider.class);
  private static final Pattern ARTIFACT_IDENTIFIER = Pattern.compile("[A-Za-z0-9.\\-_]+");

  @Override
  public @NotNull Collection<? extends AttachSourcesAction> getActions(@NotNull List<? extends LibraryOrderEntry> orderEntries,
                                                                       @NotNull PsiFile psiFile) {
    final VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return List.of();

    final String jarName = jar.getNameWithoutExtension();
    int index = jarName.lastIndexOf('-');
    if (index == -1) return List.of();

    final String version = jarName.substring(index + 1);
    final String artifactId = jarName.substring(0, index);

    if (!ARTIFACT_IDENTIFIER.matcher(version).matches() || !ARTIFACT_IDENTIFIER.matcher(artifactId).matches()) {
      return List.of();
    }

    final Set<Library> libraries = new HashSet<>();
    for (LibraryOrderEntry orderEntry : orderEntries) {
      ContainerUtil.addIfNotNull(libraries, orderEntry.getLibrary());
    }

    if (libraries.isEmpty()) return List.of();

    final String sourceFileName = jarName + "-sources.jar";

    for (Library library : libraries) {
      for (VirtualFile file : library.getFiles(OrderRootType.SOURCES)) {
        if (file.getPath().contains(sourceFileName)) {
          if (isRootInExistingFile(file)) {
            return List.of(); // Sources already attached, but source-jar doesn't contain current class.
          }
        }
      }
    }

    final File libSourceDir = getLibrarySourceDir();

    final File sourceFile = new File(libSourceDir, sourceFileName);

    if (sourceFile.exists()) {
      return List.of(new LightAttachSourcesAction() {
        @Override
        public @NlsContexts.LinkLabel @Nls(capitalization = Nls.Capitalization.Title) String getName() {
          return JavaUiBundle.message("internet.attach.source.provider.name");
        }

        @Override
        public String getBusyText() {
          return getName();
        }


        @Override
        public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
          attachSourceJar(sourceFile, libraries);
          return ActionCallback.DONE;
        }
      });
    }

    return List.of(new LightAttachSourcesAction() {
      @Override
      public @Nls(capitalization = Nls.Capitalization.Title) String getName() {
        return JavaUiBundle.message("internet.attach.source.provider.action.name");
      }

      @Override
      public String getBusyText() {
        return JavaUiBundle.message("internet.attach.source.provider.action.busy.text");
      }

      @Override
      public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
        final Task task = new Task.Modal(psiFile.getProject(), JavaUiBundle.message("progress.title.searching.source"), true) {
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
                final String title = JavaUiBundle.message("internet.attach.source.provider.action.notification.title.downloading.failed");
                showMessage(title, e.getMessage(), NotificationType.ERROR);
                continue;
              }

              if (artifactUrl != null) break;
            }

            if (artifactUrl == null) {
              showMessage(JavaUiBundle.message("internet.attach.source.provider.action.notification.title.sources.not.found"),
                          JavaUiBundle.message("internet.attach.source.provider.action.notification.content.sources.for.jar.not.found", jarName),
                          NotificationType.WARNING);
              return;
            }

            if (!(libSourceDir.isDirectory() || libSourceDir.mkdirs())) {
              showMessage(JavaUiBundle.message("internet.attach.source.provider.action.notification.title.downloading.failed"),
                          JavaUiBundle.message("internet.attach.source.provider.action.notification.content.failed.to.create.directory", libSourceDir),
                          NotificationType.ERROR);
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
              showMessage(JavaUiBundle.message("internet.attach.source.provider.action.notification.title.downloading.failed"),
                          JavaUiBundle.message("internet.attach.source.provider.action.notification.content.connection.problem"),
                          NotificationType.ERROR);
            }
          }

          @Override
          public void onSuccess() {
            attachSourceJar(sourceFile, libraries);
          }

          private void showMessage(@NlsContexts.NotificationTitle String title,
                                   @NlsContexts.NotificationContent String message,
                                   NotificationType notificationType) {
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

  public static void attachSourceJar(@NotNull File sourceJar, @NotNull Collection<? extends Library> libraries) {
    VirtualFile srcFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceJar);
    if (srcFile == null) return;

    VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(srcFile);
    if (jarRoot == null) return;

    VirtualFile[] roots = LibrarySourceRootDetectorUtil.scanAndSelectDetectedJavaSourceRoots(null, new VirtualFile[]{jarRoot});
    if (roots.length == 0) {
      roots = new VirtualFile[]{jarRoot};
    }

    doAttachSourceJars(libraries, roots);
  }

  private static void doAttachSourceJars(@NotNull Collection<? extends Library> libraries, VirtualFile[] roots) {
    WriteAction.run(() -> {
      for (Library library : libraries) {
        Library.ModifiableModel model = library.getModifiableModel();
        Set<VirtualFile> alreadyExistingFiles = ContainerUtil.newHashSet(model.getFiles(OrderRootType.SOURCES));

        for (VirtualFile root : roots) {
          if (!alreadyExistingFiles.contains(root)) {
            model.addRoot(root, OrderRootType.SOURCES);
          }
        }
        model.commit();
      }
    });
  }

  public static File getLibrarySourceDir() {
    String path = System.getProperty("idea.library.source.dir");
    return path != null ? new File(path) : new File(SystemProperties.getUserHome(), ".ideaLibSources");
  }
}