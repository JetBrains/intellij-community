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

package com.intellij.history.core;

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.util.Consumer;
import com.intellij.util.io.storage.AbstractStorage;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;

public class ChangeListStorageImpl implements ChangeListStorage {
  private static final int VERSION = 6;
  private static final String STORAGE_FILE = "changes";

  private final File myStorageDir;
  private LocalHistoryStorage myStorage;
  private long myLastId;

  private boolean isCompletelyBroken = false;

  public ChangeListStorageImpl(File storageDir) throws IOException {
    myStorageDir = storageDir;
    initStorage(myStorageDir);
  }

  private synchronized void initStorage(File storageDir) throws IOException {
    String path = storageDir.getPath() + "/" + STORAGE_FILE;

    boolean fromScratch = ApplicationManager.getApplication().isUnitTestMode() && !new File(path).exists();
    
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
        result.dispose();
        if (!FileUtil.delete(storageDir)) {
          throw new IOException("cannot clear storage dir: " + storageDir);
        }
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

  private void handleError(Throwable e, @Nullable String message) {
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
                              ",current timestamp:" + DateFormat.getDateTimeInstance().format(timestamp) +
                              ",storage timestamp:" + DateFormat.getDateTimeInstance().format(storageTimestamp) +
                              ",vfs timestamp:" + DateFormat.getDateTimeInstance().format(vfsTimestamp) + ")\n" + message, e);

    myStorage.dispose();
    try {
      FileUtil.delete(myStorageDir);
      initStorage(myStorageDir);
    }
    catch (Throwable ex) {
      LocalHistoryLog.LOG.error("cannot recreate storage", ex);
      isCompletelyBroken = true;
    }

    notifyUser("Local History storage file has become corrupted and will be rebuilt.");
  }


  public static void notifyUser(String message) {
    final String logFile = PathManager.getLogPath();
    /*String createIssuePart = "<br>" +
                             "<br>" +
                             "Please attach log files from <a href=\"file\">" + logFile + "</a><br>" +
                             "to the <a href=\"url\">YouTrack issue</a>";*/
    Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                              "Local History is broken",
                                              message /*+ createIssuePart*/,
                                              NotificationType.ERROR,
                                              new NotificationListener() {
                                                @Override
                                                public void hyperlinkUpdate(@NotNull Notification notification,
                                                                            @NotNull HyperlinkEvent event) {
                                                  if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                                                    if ("url".equals(event.getDescription())) {
                                                      BrowserUtil.browse("http://youtrack.jetbrains.net/issue/IDEA-71270");
                                                    }
                                                    else {
                                                      File file = new File(logFile);
                                                      ShowFilePathAction.openFile(file);
                                                    }
                                                  }
                                                }
                                              }), null);
  }

  public synchronized void close() {
    myStorage.dispose();
  }

  public synchronized long nextId() {
    return ++myLastId;
  }

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
    DataInputStream in = myStorage.readStream(id);
    try {
      return new ChangeSetHolder(id, new ChangeSet(in));
    }
    finally {
      in.close();
    }
  }

  public synchronized void writeNextSet(ChangeSet changeSet) {
    if (isCompletelyBroken) return;

    try {
      AbstractStorage.StorageDataOutput out = myStorage.writeStream(myStorage.createNextRecord(), true);
      try {
        changeSet.write(out);
      }
      finally {
        out.close();
      }
      myStorage.setLastId(myLastId);
      myStorage.force();
    }
    catch (IOException e) {
      handleError(e, null);
    }
  }

  public synchronized void purge(long period, int intervalBetweenActivities, Consumer<ChangeSet> processor) {
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