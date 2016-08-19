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
package com.intellij.util.download.impl;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AtomicDouble;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.IOExceptionDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author nik
 */
public class FileDownloaderImpl implements FileDownloader {
  private static final Logger LOG = Logger.getInstance(FileDownloaderImpl.class);
  private static final String LIB_SCHEMA = "lib://";

  private final List<? extends DownloadableFileDescription> myFileDescriptions;
  private final JComponent myParentComponent;
  @Nullable private final Project myProject;
  private String myDirectoryForDownloadedFilesPath;
  private final String myDialogTitle;

  public FileDownloaderImpl(@NotNull List<? extends DownloadableFileDescription> fileDescriptions,
                            @Nullable Project project,
                            @Nullable JComponent parentComponent,
                            @NotNull String presentableDownloadName) {
    myProject = project;
    myFileDescriptions = fileDescriptions;
    myParentComponent = parentComponent;
    myDialogTitle = IdeBundle.message("progress.download.0.title", StringUtil.capitalize(presentableDownloadName));
  }

  @Nullable
  @Override
  public List<VirtualFile> downloadFilesWithProgress(@Nullable String targetDirectoryPath,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent) {
    final List<Pair<VirtualFile, DownloadableFileDescription>> pairs = downloadWithProgress(targetDirectoryPath, project, parentComponent);
    if (pairs == null) return null;

    List<VirtualFile> files = new ArrayList<>();
    for (Pair<VirtualFile, DownloadableFileDescription> pair : pairs) {
      files.add(pair.getFirst());
    }
    return files;
  }

  @Nullable
  @Override
  public List<Pair<VirtualFile, DownloadableFileDescription>> downloadWithProgress(@Nullable String targetDirectoryPath,
                                                                                   @Nullable Project project,
                                                                                   @Nullable JComponent parentComponent) {
    File dir;
    if (targetDirectoryPath != null) {
      dir = new File(targetDirectoryPath);
    }
    else {
      VirtualFile virtualDir = chooseDirectoryForFiles(project, parentComponent);
      if (virtualDir != null) {
        dir = VfsUtilCore.virtualToIoFile(virtualDir);
      }
      else {
        return null;
      }
    }

    return downloadWithProcess(dir, project, parentComponent);
  }

  @Nullable
  private List<Pair<VirtualFile,DownloadableFileDescription>> downloadWithProcess(final File targetDir,
                                                                                  Project project,
                                                                                  JComponent parentComponent) {
    final Ref<List<Pair<File, DownloadableFileDescription>>> localFiles = Ref.create(null);
    final Ref<IOException> exceptionRef = Ref.create(null);

    boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        localFiles.set(download(targetDir));
      }
      catch (IOException e) {
        exceptionRef.set(e);
      }
    }, myDialogTitle, true, project, parentComponent);
    if (!completed) {
      return null;
    }

    @SuppressWarnings("ThrowableResultOfMethodCallIgnored") Exception exception = exceptionRef.get();
    if (exception != null) {
      final boolean tryAgain = IOExceptionDialog.showErrorDialog(myDialogTitle, exception.getMessage());
      if (tryAgain) {
        return downloadWithProcess(targetDir, project, parentComponent);
      }
      return null;
    }

    return findVirtualFiles(localFiles.get());
  }

  @NotNull
  @Override
  public List<Pair<File, DownloadableFileDescription>> download(@NotNull final File targetDir) throws IOException {
    final List<Pair<File, DownloadableFileDescription>> downloadedFiles = new ArrayList<>();
    final List<Pair<File, DownloadableFileDescription>> existingFiles = new ArrayList<>();
    ProgressIndicator parentIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (parentIndicator == null) {
      parentIndicator = new EmptyProgressIndicator();
    }

    try {
      final ConcurrentTasksProgressManager progressManager = new ConcurrentTasksProgressManager(parentIndicator, myFileDescriptions.size());
      parentIndicator.setText(IdeBundle.message("progress.downloading.0.files.text", myFileDescriptions.size()));
      int maxParallelDownloads = Runtime.getRuntime().availableProcessors();
      LOG.debug("Downloading " + myFileDescriptions.size() + " files using " + maxParallelDownloads + " threads");
      long start = System.currentTimeMillis();
      ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(maxParallelDownloads);
      List<Future<Void>> results = new ArrayList<>();
      final AtomicLong totalSize = new AtomicLong();
      for (final DownloadableFileDescription description : myFileDescriptions) {
        results.add(executor.submit(() -> {
          SubTaskProgressIndicator indicator = progressManager.createSubTaskIndicator();
          indicator.checkCanceled();

          final File existing = new File(targetDir, description.getDefaultFileName());
          final String url = description.getDownloadUrl();
          if (url.startsWith(LIB_SCHEMA)) {
            final String path = FileUtil.toSystemDependentName(StringUtil.trimStart(url, LIB_SCHEMA));
            final File file = PathManager.findFileInLibDirectory(path);
            existingFiles.add(Pair.create(file, description));
          }
          else if (url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
            String path = FileUtil.toSystemDependentName(StringUtil.trimStart(url, LocalFileSystem.PROTOCOL_PREFIX));
            File file = new File(path);
            if (file.exists()) {
              existingFiles.add(Pair.create(file, description));
            }
          }
          else {
            File downloaded;
            try {
              downloaded = downloadFile(description, existing, indicator);
            }
            catch (IOException e) {
              throw new IOException(IdeBundle.message("error.file.download.failed", description.getDownloadUrl(),
                                                      e.getMessage()), e);
            }
            if (FileUtil.filesEqual(downloaded, existing)) {
              existingFiles.add(Pair.create(existing, description));
            }
            else {
              totalSize.addAndGet(downloaded.length());
              downloadedFiles.add(Pair.create(downloaded, description));
            }
          }
          indicator.finished();
          return null;
        }));
      }

      for (Future<Void> result : results) {
        try {
          result.get();
        }
        catch (InterruptedException e) {
          throw new ProcessCanceledException();
        }
        catch (ExecutionException e) {
          Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
          Throwables.propagateIfInstanceOf(e.getCause(), ProcessCanceledException.class);
          LOG.error(e);
        }
      }
      long duration = System.currentTimeMillis() - start;
      LOG.debug("Downloaded " + StringUtil.formatFileSize(totalSize.get()) + " in " + StringUtil.formatDuration(duration) + "(" + duration + "ms)");

      List<Pair<File, DownloadableFileDescription>> localFiles = new ArrayList<>();
      localFiles.addAll(moveToDir(downloadedFiles, targetDir));
      localFiles.addAll(existingFiles);
      return localFiles;
    }
    catch (ProcessCanceledException e) {
      deleteFiles(downloadedFiles);
      throw e;
    }
    catch (IOException e) {
      deleteFiles(downloadedFiles);
      throw e;
    }
  }

  @Nullable
  private static VirtualFile chooseDirectoryForFiles(Project project, JComponent parentComponent) {
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
      .withTitle(IdeBundle.message("dialog.directory.for.downloaded.files.title"))
      .withDescription(IdeBundle.message("dialog.directory.for.downloaded.files.description"));
    VirtualFile baseDir = project != null ? project.getBaseDir() : null;
    return FileChooser.chooseFile(descriptor, parentComponent, project, baseDir);
  }

  private static List<Pair<File, DownloadableFileDescription>> moveToDir(List<Pair<File, DownloadableFileDescription>> downloadedFiles,
                                                                         final File targetDir) throws IOException {
    FileUtil.createDirectory(targetDir);
    List<Pair<File, DownloadableFileDescription>> result = new ArrayList<>();
    for (Pair<File, DownloadableFileDescription> pair : downloadedFiles) {
      final DownloadableFileDescription description = pair.getSecond();
      final String fileName = description.generateFileName(s -> !new File(targetDir, s).exists());
      final File toFile = new File(targetDir, fileName);
      FileUtil.rename(pair.getFirst(), toFile);
      result.add(Pair.create(toFile, description));
    }
    return result;
  }

  @NotNull
  private static List<Pair<VirtualFile, DownloadableFileDescription>> findVirtualFiles(List<Pair<File, DownloadableFileDescription>> ioFiles) {
    List<Pair<VirtualFile,DownloadableFileDescription>> result = new ArrayList<>();
    for (final Pair<File, DownloadableFileDescription> pair : ioFiles) {
      final File ioFile = pair.getFirst();
      VirtualFile libraryRootFile = new WriteAction<VirtualFile>() {
        @Override
        protected void run(@NotNull Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(ioFile);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }

      }.execute().getResultObject();
      if (libraryRootFile != null) {
        result.add(Pair.create(libraryRootFile, pair.getSecond()));
      }
    }
    return result;
  }

  private static void deleteFiles(final List<Pair<File, DownloadableFileDescription>> pairs) {
    for (Pair<File, DownloadableFileDescription> pair : pairs) {
      FileUtil.delete(pair.getFirst());
    }
  }

  @NotNull
  private static File downloadFile(@NotNull final DownloadableFileDescription description,
                                   @NotNull final File existingFile,
                                   @NotNull final ProgressIndicator indicator) throws IOException {
    final String presentableUrl = description.getPresentableDownloadUrl();
    indicator.setText2(IdeBundle.message("progress.connecting.to.download.file.text", presentableUrl));
    indicator.setIndeterminate(true);

    return HttpRequests.request(description.getDownloadUrl()).connect(new HttpRequests.RequestProcessor<File>() {
      @Override
      public File process(@NotNull HttpRequests.Request request) throws IOException {
        int size = request.getConnection().getContentLength();
        if (existingFile.exists() && size == existingFile.length()) {
          return existingFile;
        }

        indicator.setText2(IdeBundle.message("progress.download.file.text", description.getPresentableFileName(), presentableUrl));
        return request.saveToFile(FileUtil.createTempFile("download.", ".tmp"), indicator);
      }
    });
  }

  @NotNull
  @Override
  public FileDownloader toDirectory(@NotNull String directoryForDownloadedFilesPath) {
    myDirectoryForDownloadedFilesPath = directoryForDownloadedFilesPath;
    return this;
  }

  @Nullable
  @Override
  public VirtualFile[] download() {
    List<VirtualFile> files = downloadFilesWithProgress(myDirectoryForDownloadedFilesPath, myProject, myParentComponent);
    return files != null ? VfsUtilCore.toVirtualFileArray(files) : null;
  }

  @Nullable
  @Override
  public List<Pair<VirtualFile, DownloadableFileDescription>> downloadAndReturnWithDescriptions() {
    return downloadWithProgress(myDirectoryForDownloadedFilesPath, myProject, myParentComponent);
  }

  private static class ConcurrentTasksProgressManager {
    private final ProgressIndicator myParent;
    private final int myTasksCount;
    private final AtomicDouble myTotalFraction;
    private final Object myLock = new Object();
    private final LinkedHashMap<SubTaskProgressIndicator, String> myText2Stack = new LinkedHashMap<>();

    private ConcurrentTasksProgressManager(ProgressIndicator parent, int tasksCount) {
      myParent = parent;
      myTasksCount = tasksCount;
      myTotalFraction = new AtomicDouble();
    }

    public void updateFraction(double delta) {
      myTotalFraction.addAndGet(delta / myTasksCount);
      myParent.setFraction(myTotalFraction.get());
    }

    public SubTaskProgressIndicator createSubTaskIndicator() {
      return new SubTaskProgressIndicator(this);
    }

    public void setText2(@NotNull SubTaskProgressIndicator subTask, @Nullable String text) {
      if (text != null) {
        synchronized (myLock) {
          myText2Stack.put(subTask, text);
        }
        myParent.setText2(text);
      }
      else {
        String prev;
        synchronized (myLock) {
          myText2Stack.remove(subTask);
          prev = myText2Stack.getLastValue();
        }
        if (prev != null) {
          myParent.setText2(prev);
        }
      }
    }
  }

  private static class SubTaskProgressIndicator extends SensitiveProgressWrapper {
    private final AtomicDouble myFraction;
    private final ConcurrentTasksProgressManager myProgressManager;

    private SubTaskProgressIndicator(ConcurrentTasksProgressManager progressManager) {
      super(progressManager.myParent);
      myProgressManager = progressManager;
      myFraction = new AtomicDouble();
    }

    @Override
    public void setFraction(double newValue) {
      double oldValue = myFraction.getAndSet(newValue);
      myProgressManager.updateFraction(newValue - oldValue);
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
      if (myProgressManager.myTasksCount > 1) return;
      super.setIndeterminate(indeterminate);
    }

    @Override
    public void setText2(String text) {
      myProgressManager.setText2(this, text);
    }

    @Override
    public double getFraction() {
      return myFraction.get();
    }

    public void finished() {
      setFraction(1);
      myProgressManager.setText2(this, null);
    }
  }
}
