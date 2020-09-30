// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.Consumer;
import com.intellij.util.io.storage.AbstractStorage;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.MessageFormat;

public final class ChangeListStorageImpl implements ChangeListStorage {
  private static final int VERSION = 6;
  private static final @NonNls String STORAGE_FILE = "changes";

  private final Path myStorageDir;
  private LocalHistoryStorage myStorage;
  private long myLastId;

  private boolean isCompletelyBroken;

  public ChangeListStorageImpl(@NotNull Path storageDir) throws IOException {
    myStorageDir = storageDir;
    initStorage(myStorageDir);
  }

  private synchronized void initStorage(@NotNull Path storageDir) throws IOException {
    Path path = storageDir.resolve(STORAGE_FILE);

    boolean fromScratch = ApplicationManager.getApplication().isUnitTestMode() && !Files.exists(path);

    LocalHistoryStorage result = new LocalHistoryStorage(path);

    long fsTimestamp = getVFSTimestamp();

    int storedVersion = result.getVersion();
    boolean versionMismatch = storedVersion != VERSION;
    boolean timestampMismatch = result.getFSTimestamp() != fsTimestamp;
    if (versionMismatch || timestampMismatch) {
      if (!fromScratch) {
        if (versionMismatch) {
          LocalHistoryLog.LOG.info(MessageFormat.format(
            "local history version mismatch (was: {0}, expected: {1}), rebuilding...", storedVersion, VERSION));
        }
        if (timestampMismatch) LocalHistoryLog.LOG.info("FS has been rebuild, rebuilding local history...");
        Disposer.dispose(result);
        FileUtil.delete(storageDir);
        result = new LocalHistoryStorage(path);
      }
      result.setVersion(VERSION);
      result.setFSTimestamp(fsTimestamp);
    }

    myLastId = result.getLastId();
    myStorage = result;
  }

  private static long getVFSTimestamp() {
    return ManagingFS.getInstance().getCreationTimestamp();
  }

  private void handleError(Throwable e, @Nullable @NonNls String message) {
    long storageTimestamp = -1;

    long vfsTimestamp = getVFSTimestamp();
    long timestamp = System.currentTimeMillis();

    try {
      storageTimestamp = myStorage.getFSTimestamp();
    }
    catch (Exception ex) {
      LocalHistoryLog.LOG.warn("cannot read storage timestamp", ex);
    }

    LocalHistoryLog.LOG.error("Local history is broken" +
                              "(version:" + VERSION +
                              ", current timestamp: " + DateFormat.getDateTimeInstance().format(timestamp) +
                              ", storage timestamp: " + DateFormat.getDateTimeInstance().format(storageTimestamp) +
                              ", vfs timestamp: " + DateFormat.getDateTimeInstance().format(vfsTimestamp) +
                              ", path: "+myStorageDir+
                              ")\n" + message, e);

    Disposer.dispose(myStorage);
    try {
      FileUtil.delete(myStorageDir);
      initStorage(myStorageDir);
    }
    catch (Throwable ex) {
      LocalHistoryLog.LOG.error("cannot recreate storage", ex);
      isCompletelyBroken = true;
    }

    notifyUser();
  }


  private static void notifyUser() {
    /*
    final String logFile = PathManager.getLogPath();
    String createIssuePart = "<br>" +
                             "<br>" +
                             "Please attach log files from <a href=\"file\">" + logFile + "</a><br>" +
                             "to the <a href=\"url\">YouTrack issue</a>";
    NotificationListener createIssueListener = (notification, event) -> {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        if ("url".equals(event.getDescription())) {
          BrowserUtil.browse("http://youtrack.jetbrains.net/issue/IDEA-71270");
        }
        else {
          File file = new File(logFile);
          RevealFileAction.openFile(file);
        }
      }
    };
    */
    new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                     LocalHistoryBundle.message("notification.title.local.history.broken"),
                     LocalHistoryBundle.message("notification.content.local.history.broken") /*+ createIssuePart*/,
                     NotificationType.ERROR /*, createIssueListener*/).notify(null);
  }

  @Override
  public synchronized void close() {
    Disposer.dispose(myStorage);
  }

  @Override
  public synchronized long nextId() {
    return ++myLastId;
  }

  @Override
  @Nullable
  public synchronized ChangeSetHolder readPrevious(int id, TIntHashSet recursionGuard) {
    if (isCompletelyBroken) return null;

    int prevId = 0;
    try {
      prevId = id == -1 ? myStorage.getLastRecord() : doReadPrevSafely(id, recursionGuard);
      if (prevId == 0) return null;

      return doReadBlock(prevId);
    }
    catch (Throwable e) {
      String message = null;
      if (prevId != 0) {
        try {
          Pair<Long, Integer> prevOS = myStorage.getOffsetAndSize(prevId);
          long prevRecordTimestamp = myStorage.getTimestamp(prevId);
          int lastRecord = myStorage.getLastRecord();
          Pair<Long, Integer> lastOS = myStorage.getOffsetAndSize(lastRecord);
          long lastRecordTimestamp = myStorage.getTimestamp(lastRecord);

          message = "invalid record is: " + prevId + " offset: " + prevOS.first + " size: " + prevOS.second
                    + " (created " + DateFormat.getDateTimeInstance().format(prevRecordTimestamp) + ") "
                    + "last record is: " + lastRecord + " offset: " + lastOS.first + " size: " + lastOS.second
                    + " (created " + DateFormat.getDateTimeInstance().format(lastRecordTimestamp) + ")";
        }
        catch (Exception e1) {
          message = "cannot retrieve more debug info: " + e1.getMessage();
        }
      }

      handleError(e, message);
      return null;
    }
  }

  @NotNull
  private ChangeSetHolder doReadBlock(int id) throws IOException {
    try (DataInputStream in = myStorage.readStream(id)) {
      return new ChangeSetHolder(id, new ChangeSet(in));
    }
  }

  @Override
  public synchronized void writeNextSet(ChangeSet changeSet) {
    if (isCompletelyBroken) return;

    try {
      try (AbstractStorage.StorageDataOutput out = myStorage.writeStream(myStorage.createNextRecord(), true)) {
        changeSet.write(out);
      }
      myStorage.setLastId(myLastId);
      myStorage.force();
    }
    catch (IOException e) {
      handleError(e, null);
    }
  }

  @Override
  public synchronized void purge(long period, int intervalBetweenActivities, Consumer<? super ChangeSet> processor) {
    if (isCompletelyBroken) return;

    TIntHashSet recursionGuard = new TIntHashSet(1000);

    try {
      int firstObsoleteId = findFirstObsoleteBlock(period, intervalBetweenActivities, recursionGuard);
      if (firstObsoleteId == 0) return;

      int eachBlockId = firstObsoleteId;

      while (eachBlockId != 0) {
        processor.consume(doReadBlock(eachBlockId).changeSet);
        eachBlockId = doReadPrevSafely(eachBlockId, recursionGuard);
      }
      myStorage.deleteRecordsUpTo(firstObsoleteId);
      myStorage.force();
    }
    catch (IOException e) {
      handleError(e, null);
    }
  }

  private int findFirstObsoleteBlock(long period, int intervalBetweenActivities, TIntHashSet recursionGuard) throws IOException {
    long prevTimestamp = 0;
    long length = 0;

    int last = myStorage.getLastRecord();
    while (last != 0) {
      long t = myStorage.getTimestamp(last);
      if (prevTimestamp == 0) prevTimestamp = t;

      long delta = prevTimestamp - t;
      prevTimestamp = t;

      // we sum only intervals between changes during one 'day' (intervalBetweenActivities) and add '1' between two 'days'
      length += delta < intervalBetweenActivities ? delta : 1;

      if (length >= period) return last;

      last = doReadPrevSafely(last, recursionGuard);
    }

    return 0;
  }

  private int doReadPrevSafely(int id, TIntHashSet recursionGuard) throws IOException {
    recursionGuard.add(id);
    int prev = myStorage.getPrevRecord(id);
    if (!recursionGuard.add(prev)) throw new IOException("Recursive records found");
    return prev;
  }
}