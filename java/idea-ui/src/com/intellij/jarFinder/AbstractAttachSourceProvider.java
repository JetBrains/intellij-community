// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarFinder;

import com.intellij.codeInsight.AttachSourcesProvider;
import com.intellij.ide.JavaUiBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractAttachSourceProvider implements AttachSourcesProvider {

  private static final Logger LOG = Logger.getInstance(AbstractAttachSourceProvider.class);

  protected static @Nullable VirtualFile getJarByPsiFile(@NotNull PsiFile psiFile) {
    VirtualFile entry = psiFile.getVirtualFile();
    if (entry != null) {
      VirtualFileSystem fs = entry.getFileSystem();
      if (fs instanceof JarFileSystem) {
        return ((JarFileSystem)fs).getLocalByEntry(entry);
      }
    }

    return null;
  }

  protected static @Nullable Library getLibraryFromOrderEntriesList(@NotNull List<? extends LibraryOrderEntry> orderEntries) {
    if (orderEntries.isEmpty()) return null;

    Library library = orderEntries.get(0).getLibrary();
    if (library == null) return null;

    for (int i = 1; i < orderEntries.size(); i++) {
      if (!library.equals(orderEntries.get(i).getLibrary())) {
        return null;
      }
    }

    return library;
  }

  protected void addSourceFile(@Nullable VirtualFile jarRoot, @NotNull Library library) {
    if (jarRoot != null) {
      if (!Arrays.asList(library.getFiles(OrderRootType.SOURCES)).contains(jarRoot)) {
        Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(jarRoot, OrderRootType.SOURCES);
        model.commit();
      }
    }
  }

  protected class AttachExistingSourceAction implements AttachSourcesAction {
    private final @Nls(capitalization = Nls.Capitalization.Title) String myName;
    private final VirtualFile mySrcFile;
    private final Library myLibrary;

    public AttachExistingSourceAction(VirtualFile srcFile, Library library, @Nls(capitalization = Nls.Capitalization.Title) String actionName) {
      mySrcFile = srcFile;
      myLibrary = library;
      myName = actionName;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getBusyText() {
      return getName();
    }

    @Override
    public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      ActionCallback callback = new ActionCallback();
      callback.setDone();

      if (!mySrcFile.isValid()) return callback;

      if (myLibrary != getLibraryFromOrderEntriesList(orderEntriesContainingFile)) return callback;

      WriteAction.run(() -> addSourceFile(mySrcFile, myLibrary));

      return callback;
    }
  }

  protected abstract static class DownloadSourcesAction implements AttachSourcesAction {
    protected final Project myProject;
    protected final String myUrl;
    protected final String myMessageGroupId;

    public DownloadSourcesAction(Project project, String messageGroupId, String url) {
      myProject = project;
      myUrl = url;
      myMessageGroupId = messageGroupId;
    }

    @Override
    public String getName() {
      return JavaUiBundle.message("attach.source.provider.download.sources.action.name");
    }

    @Override
    public String getBusyText() {
      return JavaUiBundle.message("attach.source.provider.download.sources.action.busy.text");
    }

    protected abstract void storeFile(byte[] content);

    @Override
    public @NotNull ActionCallback perform(@NotNull List<? extends LibraryOrderEntry> orderEntriesContainingFile) {
      final ActionCallback callback = new ActionCallback();
      Task task = new Task.Backgroundable(myProject, JavaUiBundle.message("progress.title.downloading.sources"), true) {
        @Override
        public void run(final @NotNull ProgressIndicator indicator) {
          final byte[] bytes;
          try {
            LOG.info("Downloading sources JAR: " + myUrl);
            indicator.checkCanceled();
            bytes = HttpRequests.request(myUrl).readBytes(indicator);
          }
          catch (IOException e) {
            LOG.warn(e);
            ApplicationManager.getApplication().invokeLater(() -> {
              String message = JavaUiBundle.message("error.message.failed.to.download.sources.0", myUrl);
              new Notification(myMessageGroupId, JavaUiBundle.message("notification.title.downloading.failed"), message, NotificationType.ERROR).notify(getProject());
              callback.setDone();
            });
            return;
          }

          ApplicationManager.getApplication().invokeLater(() -> {
            try {
              WriteAction.run(() -> storeFile(bytes));
            }
            finally {
              callback.setDone();
            }
          });
        }

        @Override
        public void onCancel() {
          callback.setRejected();
        }
      };

      task.queue();

      return callback;
    }
  }
}