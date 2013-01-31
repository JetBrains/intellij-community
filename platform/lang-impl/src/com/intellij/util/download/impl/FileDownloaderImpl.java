/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.IOExceptionDialog;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class FileDownloaderImpl implements FileDownloader {
  private static final int CONNECTION_TIMEOUT = 60*1000;
  private static final int READ_TIMEOUT = 60*1000;
  @NonNls private static final String LIB_SCHEMA = "lib://";

  private List<? extends DownloadableFileDescription> myFileDescriptions;
  private JComponent myParent;
  private @Nullable Project myProject;
  private String myDirectoryForDownloadedFilesPath;
  private String myDialogTitle;

  public FileDownloaderImpl(final List<? extends DownloadableFileDescription> fileDescriptions,
                            final @Nullable Project project,
                            JComponent parent,
                            @NotNull String presentableDownloadName) {
    myProject = project;
    myFileDescriptions = fileDescriptions;
    myParent = parent;
    myDialogTitle = IdeBundle.message("progress.download.0.title", StringUtil.capitalize(presentableDownloadName));
  }

  @NotNull
  @Override
  public FileDownloader toDirectory(@NotNull String directoryForDownloadedFilesPath) {
    myDirectoryForDownloadedFilesPath = directoryForDownloadedFilesPath;
    return this;
  }

  @Override
  public VirtualFile[] download() {
    final List<Pair<VirtualFile, DownloadableFileDescription>> pairs = downloadAndReturnWithDescriptions();
    if (pairs == null) return null;

    List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (Pair<VirtualFile, DownloadableFileDescription> pair : pairs) {
      files.add(pair.getFirst());
    }
    return VfsUtilCore.toVirtualFileArray(files);
  }

  @Override
  public List<Pair<VirtualFile, DownloadableFileDescription>> downloadAndReturnWithDescriptions() {
    VirtualFile dir = null;
    if (myDirectoryForDownloadedFilesPath != null) {
      File ioDir = new File(FileUtil.toSystemDependentName(myDirectoryForDownloadedFilesPath));
      ioDir.mkdirs();
      dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(myDirectoryForDownloadedFilesPath);
    }

    if (dir == null) {
      dir = chooseDirectoryForFiles();
    }

    if (dir != null) {
      return doDownload(dir);
    }
    return null;
  }

  @Nullable
  private List<Pair<VirtualFile,DownloadableFileDescription>> doDownload(final VirtualFile dir) {
    final List<Pair<File, DownloadableFileDescription>> downloadedFiles = new ArrayList<Pair<File, DownloadableFileDescription>>();
    final List<Pair<File, DownloadableFileDescription>> existingFiles = new ArrayList<Pair<File, DownloadableFileDescription>>();
    final Ref<Exception> exceptionRef = Ref.create(null);
    final Ref<DownloadableFileDescription> currentFile = new Ref<DownloadableFileDescription>();
    final File ioDir = VfsUtilCore.virtualToIoFile(dir);

    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        try {
          for (int i = 0; i < myFileDescriptions.size(); i++) {
            DownloadableFileDescription description = myFileDescriptions.get(i);
            currentFile.set(description);
            if (indicator != null) {
              indicator.checkCanceled();
              indicator.setText(IdeBundle.message("progress.downloading.0.of.1.file.text", i+1, myFileDescriptions.size()));
            }

            final File existing = new File(ioDir, description.getDefaultFileName());
            long size = existing.exists() ? existing.length() : -1;

            if (!download(description, size, downloadedFiles)) {
              existingFiles.add(Pair.create(existing, description));
            }
          }
        }
        catch (ProcessCanceledException e) {
          exceptionRef.set(e);
        }
        catch (IOException e) {
          exceptionRef.set(e);
        }
      }
    }, myDialogTitle, true, myProject, myParent);

    Exception exception = exceptionRef.get();
    if (exception != null) {
      deleteFiles(downloadedFiles);
      if (exception instanceof IOException) {
        String message = IdeBundle.message("error.file.download.failed", exception.getMessage());
        if (currentFile.get() != null) {
          message += ": " + currentFile.get().getDownloadUrl();
        }
        final boolean tryAgain = IOExceptionDialog.showErrorDialog(myDialogTitle, message);
        if (tryAgain) {
          return doDownload(dir);
        }
      }
      return null;
    }

    try {
      return moveToDir(existingFiles, downloadedFiles, dir);
    }
    catch (IOException e) {
      if (myProject != null) {
        Messages.showErrorDialog(myProject, myDialogTitle, e.getMessage());
      }
      else {
        Messages.showErrorDialog(myParent, myDialogTitle, e.getMessage());
      }
      return null;
    }
  }

  @Nullable
  private VirtualFile chooseDirectoryForFiles() {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    descriptor.setTitle(IdeBundle.message("dialog.directory.for.downloaded.files.title"));
    final VirtualFile baseDir = myProject != null ? myProject.getBaseDir() : null;
    return FileChooser.chooseFile(descriptor, myParent, myProject, baseDir);
  }

  @NotNull
  private static List<Pair<VirtualFile,DownloadableFileDescription>> moveToDir(final List<Pair<File, DownloadableFileDescription>> existingFiles, final List<Pair<File, DownloadableFileDescription>> downloadedFiles, final VirtualFile dir) throws IOException {
    List<Pair<VirtualFile,DownloadableFileDescription>> result = new ArrayList<Pair<VirtualFile, DownloadableFileDescription>>();

    final File ioDir = VfsUtilCore.virtualToIoFile(dir);
    for (Pair<File, DownloadableFileDescription> pair : downloadedFiles) {
      final DownloadableFileDescription description = pair.getSecond();
      final boolean dontTouch = description.getDownloadUrl().startsWith(LIB_SCHEMA) || description.getDownloadUrl().startsWith(LocalFileSystem.PROTOCOL_PREFIX);
      final File toFile = dontTouch? pair.getFirst() : generateName(description, ioDir);
      if (!dontTouch) {
        FileUtil.rename(pair.getFirst(), toFile);
      }
      VirtualFile file = new WriteAction<VirtualFile>() {
        @Override
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(toFile);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(toFile);
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }
      }.execute().getResultObject();
      if (file != null) {
        result.add(Pair.create(file, description));
      }
    }

    for (final Pair<File, DownloadableFileDescription> pair : existingFiles) {
      VirtualFile libraryRootFile = new WriteAction<VirtualFile>() {
        @Override
        protected void run(final Result<VirtualFile> result) {
          final String url = VfsUtil.getUrlForLibraryRoot(pair.getFirst());
          result.setResult(VirtualFileManager.getInstance().refreshAndFindFileByUrl(url));
        }

      }.execute().getResultObject();
      if (libraryRootFile != null) {
        result.add(Pair.create(libraryRootFile, pair.getSecond()));
      }
    }
    return result;
  }

  private static File generateName(DownloadableFileDescription info, final File dir) {
    final String fileName = info.generateFileName(new Condition<String>() {
      @Override
      public boolean value(String s) {
        return !new File(dir, s).exists();
      }
    });
    return new File(dir, fileName);
  }

  private static void deleteFiles(final List<Pair<File, DownloadableFileDescription>> pairs) {
    for (Pair<File, DownloadableFileDescription> pair : pairs) {
      FileUtil.delete(pair.getFirst());
    }
    pairs.clear();
  }

  private static boolean download(final DownloadableFileDescription fileDescription,
                                  final long existingFileSize,
                                  final List<Pair<File,DownloadableFileDescription>> downloadedFiles) throws IOException {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final String presentableUrl = fileDescription.getPresentableDownloadUrl();
    final String url = fileDescription.getDownloadUrl();
    if (url.startsWith(LIB_SCHEMA)) {
      indicator.setText2(IdeBundle.message("progress.locate.file.text", fileDescription.getPresentableFileName()));
      final String path = FileUtil.toSystemDependentName(StringUtil.trimStart(url, LIB_SCHEMA));
      final File file = PathManager.findFileInLibDirectory(path);
      downloadedFiles.add(Pair.create(file, fileDescription));
    }
    else if (url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
      String path = FileUtil.toSystemDependentName(StringUtil.trimStart(url, LocalFileSystem.PROTOCOL_PREFIX));
      File file = new File(path);
      if (file.exists()) {
        downloadedFiles.add(Pair.create(file, fileDescription));
      }
    }
    else {
      indicator.setText2(IdeBundle.message("progress.connecting.to.download.file.text", presentableUrl));
      indicator.setIndeterminate(true);
      HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
      connection.setConnectTimeout(CONNECTION_TIMEOUT);
      connection.setReadTimeout(READ_TIMEOUT);

      InputStream input = null;
      BufferedOutputStream output = null;

      boolean deleteFile = true;
      File tempFile = null;
      try {
        final int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
        }

        final int size = connection.getContentLength();
        if (size != -1 && size == existingFileSize) {
          return false;
        }

        tempFile = FileUtil.createTempFile("downloaded", "file");
        input = UrlConnectionUtil.getConnectionInputStreamWithException(connection, indicator);
        output = new BufferedOutputStream(new FileOutputStream(tempFile));
        indicator.setText2(IdeBundle.message("progress.download.file.text", fileDescription.getPresentableFileName(), presentableUrl));
        indicator.setIndeterminate(size == -1);

        NetUtils.copyStreamContent(indicator, input, output, size);

        deleteFile = false;
        downloadedFiles.add(Pair.create(tempFile, fileDescription));
      }
      finally {
        if (input != null) {
          input.close();
        }
        if (output != null) {
          output.close();
        }
        if (deleteFile && tempFile != null) {
          FileUtil.delete(tempFile);
        }
        connection.disconnect();
      }
    }
    return true;
  }
}
