// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.file;

import com.google.common.collect.EvictingQueue;
import com.intellij.largeFilesEditor.accessGettingPageTokens.AccessGettingPageToken;
import com.intellij.largeFilesEditor.changes.ChangesManager;
import com.intellij.largeFilesEditor.editor.Page;
import com.intellij.largeFilesEditor.search.searchTask.FileDataProviderForSearch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ConcurrencyUtil;
import org.apache.commons.lang.NotImplementedException;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FileManagerImpl implements FileManager {
  private static final Logger logger = Logger.getInstance(FileManagerImpl.class);
  private static final int MAX_SIZE_OF_PAGE_CASH = 3;

  private final LoadPageCallback loadPageCallback;
  private final SaveFileCallback saveFileCallback;
  private final ChangesManager changesManager;

  private FileAdapter fileAdapter;
  private final Queue<Page> notUpdatedPagesCash;

  private final ExecutorService needToOpenNewPageExecutor = new ThreadPoolExecutor(
    1, 1, 0L, TimeUnit.MILLISECONDS, new OnePlacePushingQueue<>());
  private final ExecutorService savingFileExecutor =
    ConcurrencyUtil.newSingleThreadExecutor("Large File Editor Saving File Executor");
  private final ExecutorService readingPageExecutor =
    ConcurrencyUtil.newSingleThreadExecutor("Large File Editor Reading File Executor");

  private SavingFileTask lastExecutedSavingFileTask;


  public FileManagerImpl(VirtualFile vFile, int pageSize, int maxPageBorderShift,
                         LoadPageCallback loadPageCallback, SaveFileCallback saveFileCallback,
                         ChangesManager changesManager) throws FileNotFoundException {
    this.loadPageCallback = loadPageCallback;
    this.saveFileCallback = saveFileCallback;
    this.changesManager = changesManager;
    this.fileAdapter = new FileAdapter(pageSize, maxPageBorderShift, vFile);

    notUpdatedPagesCash = EvictingQueue.create(MAX_SIZE_OF_PAGE_CASH);
  }

  @Override
  synchronized public void reset(Charset charset) {
    notUpdatedPagesCash.clear();
    fileAdapter.setCharset(charset);
  }

  @CalledInAwt
  @Override
  public void needToOpenNewPage(AccessGettingPageToken token) {
    needToOpenNewPageExecutor.execute(new NeedToOpenNewPageTask(token));
  }

  @Override
  public void dispose() {
    needToOpenNewPageExecutor.shutdown();
    savingFileExecutor.shutdown();
    try {
      fileAdapter.closeFile();
    }
    catch (IOException e) {
      logger.warn(e);
    }
  }

  @Override
  public String getCharsetName() {
    return fileAdapter.getCharsetName();
  }

  @Override
  public long getPagesAmount() throws IOException {
    return fileAdapter.getPagesAmount();
  }

  @Override
  public int getPageSize() {
    return fileAdapter.getPageSize();
  }

  @Override
  public int getMaxBorderShift() {
    return fileAdapter.getMaxBorderShift();
  }

  /**
   * Warning! Thread-blocking method
   *
   * @param pageNumber - page number
   * @return updated page for specified page number.
   * @throws IOException - error working with file`
   */
  @Override
  @NotNull
  synchronized public Page getPage_wait(long pageNumber) throws IOException {
    String notUpdatedPageText;

    Page notUpdatedPage = null;
    for (Page page : notUpdatedPagesCash) {
      if (page.getPageNumber() == pageNumber) {
        notUpdatedPage = page;
      }
    }

    if (notUpdatedPage != null) {
      notUpdatedPageText = notUpdatedPage.getText();
    }
    else {
      notUpdatedPageText = fileAdapter.getPageText(pageNumber);
      notUpdatedPage = new Page(notUpdatedPageText, pageNumber);
      notUpdatedPagesCash.add(notUpdatedPage);
    }

    return new Page(changesManager.makePageTextUpToDate(notUpdatedPageText, pageNumber), pageNumber);
  }

  @CalledInAwt
  @Override
  public void beginSavingFile() {
    lastExecutedSavingFileTask = new SavingFileTask();
    savingFileExecutor.execute(lastExecutedSavingFileTask);
  }

  @CalledInAwt
  @Override
  public void cancelSaving() {
    if (lastExecutedSavingFileTask != null) {
      lastExecutedSavingFileTask.shouldCancel();
    }
  }

  @Override
  public boolean canFileBeReloadedInOtherCharset() {
    if (fileAdapter == null) {
      return false;
    }

    VirtualFile vFile = fileAdapter.getVirtualFile();
    return vFile.getBOM() == null || vFile.getBOM().length == 0;
  }

  @Override
  public String getFileName() {
    return fileAdapter.getFileName();
  }

  @Override
  public FileDataProviderForSearch getFileDataProviderForSearch() {
    return new FileDataProviderForSearch() {
      @Override
      public long getPagesAmount() throws IOException {
        return FileManagerImpl.this.getPagesAmount();
      }

      @Override
      public Page getPage_wait(long pageNumber) throws IOException {
        return FileManagerImpl.this.getPage_wait(pageNumber);
      }

      @Override
      public String getName() {
        return FileManagerImpl.this.getFileName();
      }
    };
  }

  @Override
  public void requestReadPage(long pageNumber, ReadingPageResultHandler readingPageResultHandler) {
    readingPageExecutor.execute(() -> {
      try {
        Page page = getPage_wait(pageNumber);
        readingPageResultHandler.run(page);
      }
      catch (IOException e) {
        logger.warn(e);
        readingPageResultHandler.run(null);
      }
    });
  }

  private class NeedToOpenNewPageTask implements Runnable {
    private final AccessGettingPageToken token;

    NeedToOpenNewPageTask(AccessGettingPageToken token) {
      this.token = token;
    }

    @Override
    public void run() {
      Page page;

      try {
        page = getPage_wait(token.getPageNumber());
        loadPageCallback.tellPageIsLoaded(page, token);
      }
      catch (IOException e) {
        logger.warn(e);
        loadPageCallback.tellCatchedErrorWhileLoadingPage();
      }
    }
  }

  /**
   * It is queue with fixed capacity = 1.
   * When someone tries to add new element in it, the new element will be added anyway.
   * If the only place was occupied by any old element, than the new element will be
   * added instead of the old element.
   *
   * @param <E>
   */
  public static class OnePlacePushingQueue<E> extends LinkedBlockingQueue<E> {
    OnePlacePushingQueue() {
      super(1);   // one place
    }

    @Deprecated
    @SuppressWarnings("unused")
    public OnePlacePushingQueue(int capacity) {
      throw new NotImplementedException("This method isn't allowed!");
    }

    @Deprecated
    @SuppressWarnings("unused")
    public OnePlacePushingQueue(Collection c) {
      throw new NotImplementedException("This method isn't allowed!");
    }

    @Override
    synchronized public void put(E o) throws InterruptedException {
      super.clear();
      super.put(o);
    }

    @Override
    synchronized public boolean offer(E o, long timeout, TimeUnit unit) throws InterruptedException {
      super.clear();
      return super.offer(o, timeout, unit);
    }

    @Override
    synchronized public boolean offer(@NotNull E o) {
      super.clear();
      return super.offer(o);
    }
  }

  private class SavingFileTask implements Runnable {
    private final TaskState taskState;
    private volatile boolean shouldCancel = false;

    SavingFileTask() {
      taskState = new TaskState();
      taskState.progressState = ProgressState.AtStart;
      taskState.largeFileAdapterState = LargeFileAdapterState.OpenedBeforeExchangingOldAndNewFiles;
      taskState.oldFileLocationState = FileLocationState.NotExist;
      taskState.newFileLocationState = FileLocationState.NotExist;
      taskState.oldFileLocationPath = null;
      taskState.newFileLocationPath = null;
    }

    TaskState getTaskState() {
      return taskState;
    }

    /**
     * Terms of use: can be called from any thread
     */
    boolean isShouldCancel() {
      return shouldCancel;
    }

    /**
     * Terms of use: can be called from any thread
     */
    void shouldCancel() {
      shouldCancel = true;
    }

    @Override
    public void run() {
      perform();
      tellSavingFileTaskIsFinished(this);
    }

    private void perform() {
      boolean actionResult;


      taskState.progressState = ProgressState.Processing;


      Path normPath = Paths.get(fileAdapter.getVirtualFile().getPath());
      Path tempPathForOldFile = getUnusedFilePath(normPath, ".old.temp");
      Path tempPathForNewFile = getUnusedFilePath(normPath, ".new.temp");


      if (!normPath.toFile().exists()) {
        taskState.oldFileLocationState = FileLocationState.NotExist;
        taskState.progressState = ProgressState.AbortedAndNotReestablished;
        return;
      }
      else {
        taskState.oldFileLocationState = FileLocationState.AtNormPath;
        taskState.oldFileLocationPath = normPath.toString();
      }


      actionResult = tryWriteNewFileToTempPath(this, fileAdapter, tempPathForNewFile);
      if (tempPathForNewFile.toFile().exists()) {
        taskState.newFileLocationState = FileLocationState.AtTempPath;
        taskState.newFileLocationPath = tempPathForNewFile.toString();
      }
      else {
        taskState.newFileLocationState = FileLocationState.NotExist;
      }
      if (!actionResult) {
        if (taskState.newFileLocationState == FileLocationState.NotExist) {
          taskState.progressState = ProgressState.AbortedAndReestablished;
        }
        else {
          taskState.progressState = ProgressState.AbortedAndNotReestablished;
        }
        return;
      }


      actionResult = tryCloseLargeFileAdapter(fileAdapter);
      if (!actionResult) {
        taskState.progressState = ProgressState.AbortedAndNotReestablished;
        taskState.largeFileAdapterState = LargeFileAdapterState.ErrorWhileClosing;
        return;
      }
      taskState.largeFileAdapterState = LargeFileAdapterState.Closed;


      actionResult = tryMoveFile(normPath, tempPathForOldFile);  // moving old file outside
      if (tempPathForOldFile.toFile().exists()) {
        taskState.oldFileLocationState = FileLocationState.AtTempPath;
        taskState.oldFileLocationPath = tempPathForOldFile.toString();
      }
      else if (normPath.toFile().exists()) {
        taskState.oldFileLocationState = FileLocationState.AtNormPath;
        taskState.oldFileLocationPath = normPath.toString();
      }
      else {
        taskState.oldFileLocationState = FileLocationState.NotExist;
      }
      if (!actionResult || taskState.oldFileLocationState == FileLocationState.NotExist) {
        taskState.progressState = ProgressState.AbortedAndNotReestablished;
        return;
      }


      actionResult = tryMoveFile(tempPathForNewFile, normPath);  // moving new file inside
      if (normPath.toFile().exists()) {
        taskState.newFileLocationState = FileLocationState.AtNormPath;
        taskState.newFileLocationPath = normPath.toString();
      }
      else if (tempPathForNewFile.toFile().exists()) {
        taskState.newFileLocationState = FileLocationState.AtTempPath;
        taskState.newFileLocationPath = tempPathForNewFile.toString();
      }
      else {
        taskState.newFileLocationState = FileLocationState.NotExist;
      }
      if (!actionResult || taskState.newFileLocationState == FileLocationState.NotExist) {
        taskState.progressState = ProgressState.AbortedAndNotReestablished;
        return;
      }

      notUpdatedPagesCash.clear();
      changesManager.clear();

      actionResult = tryDeleteFile(tempPathForOldFile);
      if (tempPathForOldFile.toFile().exists()) {
        taskState.oldFileLocationState = FileLocationState.AtTempPath;
        taskState.oldFileLocationPath = tempPathForOldFile.toString();
      }
      else {
        taskState.oldFileLocationState = FileLocationState.NotExist;
      }
      if (!actionResult || taskState.oldFileLocationState != FileLocationState.NotExist) {
        taskState.progressState = ProgressState.AbortedAndNotReestablished;
        return;
      }


      fileAdapter = tryReopenLargeFileAdapter(fileAdapter);
      if (fileAdapter == null) {
        taskState.largeFileAdapterState = LargeFileAdapterState.ErrorWhileOpenning;
        taskState.progressState = ProgressState.AbortedAndNotReestablished;
        return;
      }
      taskState.largeFileAdapterState = LargeFileAdapterState.OpenedAfterExchangingOldAndNewFiles;

      taskState.progressState = ProgressState.FinishedSuccessful;
    }


    private boolean tryWriteNewFileToTempPath(SavingFileTask caller, FileAdapter fileAdapter, Path tempPathForNewFile) {

      try (FileOutputStream fileOutputStream = new FileOutputStream(tempPathForNewFile.toString())) {

        int prevProgress = 0;
        int progress;
        long amountOfPages;
        byte[] pageTextData;

        Charset charset = fileAdapter.getCharset();
        byte[] bom = CharsetToolkit.getPossibleBom(charset);
        if (bom != null) {
          fileOutputStream.write(bom);
        }

        for (long pageNumber = 0; pageNumber < (amountOfPages = fileAdapter.getPagesAmount()); pageNumber++) {

          if (caller.isShouldCancel()) {
            break;
          }

          progress = (int)(pageNumber * 100 / amountOfPages);
          if (progress > prevProgress) {
            prevProgress = progress;
            tellProgress(progress);
          }

          Page page = getPage_wait(pageNumber);
          pageTextData = page.getText().getBytes(charset);
          fileOutputStream.write(pageTextData);
        }
      }
      catch (IOException e) {
        logger.warn(e);
        return false;
      }

      if (!caller.isShouldCancel()) {
        return true;
      }

      try {
        Files.delete(tempPathForNewFile);
      }
      catch (IOException e) {
        logger.warn(e);
      }
      return false;
    }

    private boolean tryCloseLargeFileAdapter(FileAdapter fileAdapter) {
      try {
        fileAdapter.closeFile();
        return true;
      }
      catch (IOException e) {
        logger.warn(e);
        return false;
      }
    }

    private boolean tryMoveFile(Path fromPath, Path toPath) {
      try {
        Files.move(fromPath, toPath);
        return true;
      }
      catch (IOException e) {
        logger.warn(e);
        return false;
      }
    }

    /**
     * @return true - if the file was successfully deleted by this method, false - otherwise
     */
    private boolean tryDeleteFile(Path path) {
      try {
        Files.delete(path);
        return true;
      }
      catch (IOException e) {
        logger.warn(e);
        return false;
      }
    }


    private FileAdapter tryReopenLargeFileAdapter(FileAdapter oldFileAdapter) {
      try {
        return new FileAdapter(oldFileAdapter.getPageSize(),
                               oldFileAdapter.getMaxBorderShift(), oldFileAdapter.getVirtualFile());
      }
      catch (FileNotFoundException e) {
        logger.warn(e);
        return null;
      }
    }


    private Path getUnusedFilePath(Path normPath, String suffix) {
      int counter = 0;
      String path = normPath.toString() + suffix;
      while (Paths.get(path).toFile().exists()) {
        counter++;
        path = normPath.toString() + suffix + counter;
      }
      return Paths.get(path);
    }

    private void tellSavingFileTaskIsFinished(SavingFileTask caller) {
      TaskState taskState = caller.getTaskState();
      if (taskState.progressState == ProgressState.FinishedSuccessful) {
        saveFileCallback.tellSavingFileWasCompleteSuccessfully();
      }
      else if (taskState.progressState == ProgressState.AbortedAndReestablished && caller.isShouldCancel()) {
        saveFileCallback.tellSavingFileWasCanceledAndEverythingWasReestablished();
      }
      else {
        StringBuilder msg = new StringBuilder();
        msg.append("An error was occurred while saving the file.");
        msg.append("\nCurrent state of versions of the file:");

        msg.append("\n\n  ");
        if (taskState.oldFileLocationState != FileLocationState.NotExist) {
          msg.append("The old version of file exists as ").append(taskState.oldFileLocationPath);
        }
        else {
          msg.append("The old version of file doesn't exist any more");
        }

        msg.append("\n\n  ");
        if (taskState.newFileLocationState != FileLocationState.NotExist) {
          msg.append("The new version of file exists as ").append(taskState.newFileLocationPath);
        }
        else {
          msg.append("The new version of file doesn't exist any more");
        }

        msg.append("\n\n  ");
        if (taskState.oldFileLocationState == FileLocationState.AtNormPath) {
          msg.append("You may try to reopen the file if you want to open the old version of it");
        }
        else if (taskState.newFileLocationState == FileLocationState.AtNormPath) {
          msg.append("You may try to reopen the file if you want to open the new version of it");
        }
        else {
          msg.append("You should reopen the editor.");
        }

        saveFileCallback.tellSavingFileWasCorrupted(msg.toString());
      }
    }

    private void tellProgress(int progress) {
      saveFileCallback.tellSavingProgress(progress);
    }
  }


  private static class TaskState {
    ProgressState progressState;
    FileLocationState oldFileLocationState;
    FileLocationState newFileLocationState;
    LargeFileAdapterState largeFileAdapterState;

    String oldFileLocationPath;
    String newFileLocationPath;
  }

  enum ProgressState {
    AtStart,
    Processing,
    AbortedAndReestablished,
    AbortedAndNotReestablished,
    FinishedSuccessful,
  }

  enum FileLocationState {
    AtNormPath,
    AtTempPath,
    NotExist,
  }

  enum LargeFileAdapterState {
    OpenedBeforeExchangingOldAndNewFiles,
    ErrorWhileClosing,
    Closed,
    ErrorWhileOpenning,
    OpenedAfterExchangingOldAndNewFiles,
  }
}

