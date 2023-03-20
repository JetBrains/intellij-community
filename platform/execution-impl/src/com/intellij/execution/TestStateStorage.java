// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.FlushingDaemon;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Dmitry Avdeev
 */
public class TestStateStorage implements Disposable {

  private static final File TEST_HISTORY_PATH = new File(PathManager.getSystemPath(), "testHistory");

  private static final int CURRENT_VERSION = 5;

  private final File myFile;

  public static File getTestHistoryRoot(@NotNull Project project) {
    return new File(TEST_HISTORY_PATH, project.getLocationHash());
  }

  public static class Record {
    public final int magnitude;
    public final long configurationHash;
    public final Date date;
    public int failedLine;
    public String failedMethod;
    public final @NlsSafe String errorMessage;
    public final String topStacktraceLine;

    public Record(int magnitude, Date date, long configurationHash, int failLine, String method, String errorMessage, String topStacktraceLine) {
      this.magnitude = magnitude;
      this.date = date;
      this.configurationHash = configurationHash;
      this.failedLine = failLine;
      failedMethod = method;
      this.errorMessage = errorMessage;
      this.topStacktraceLine = topStacktraceLine;
    }
  }

  private static final Logger LOG = Logger.getInstance(TestStateStorage.class);
  @Nullable
  private PersistentHashMap<String, Record> myMap;
  private volatile ScheduledFuture<?> myMapFlusher;

  public static TestStateStorage getInstance(@NotNull Project project) {
    return project.getService(TestStateStorage.class);
  }

  public TestStateStorage(Project project) {
    String directoryPath = getTestHistoryRoot(project).getPath();

    myFile = new File(directoryPath + "/testStateMap");
    FileUtilRt.createParentDirs(myFile);

    try {
      myMap = initializeMap();
    } catch (IOException e) {
      LOG.error(e);
    }
    myMapFlusher = FlushingDaemon.everyFiveSeconds(this::flushMap);
  }

  private PersistentHashMap<String, Record> initializeMap() throws IOException {
    return IOUtil.openCleanOrResetBroken(getComputable(myFile), myFile);
  }

  private synchronized void flushMap() {
    if (myMapFlusher == null) return; // disposed
    if (myMap != null && myMap.isDirty()) myMap.force();
  }

  @NotNull
  private static ThrowableComputable<PersistentHashMap<String, Record>, IOException> getComputable(final File file) {
    return () -> new PersistentHashMap<>(file.toPath(), EnumeratorStringDescriptor.INSTANCE, new DataExternalizer<Record>() {
      @Override
      public void save(@NotNull DataOutput out, Record value) throws IOException {
        out.writeInt(value.magnitude);
        out.writeLong(value.date.getTime());
        out.writeLong(value.configurationHash);
        out.writeInt(value.failedLine);
        out.writeUTF(StringUtil.notNullize(value.failedMethod));
        out.writeUTF(StringUtil.notNullize(value.errorMessage));
        out.writeUTF(StringUtil.notNullize(value.topStacktraceLine));
      }

      @Override
      public Record read(@NotNull DataInput in) throws IOException {
        return new Record(in.readInt(), new Date(in.readLong()), in.readLong(), in.readInt(), in.readUTF(), in.readUTF(), in.readUTF());
      }
    }, 4096, CURRENT_VERSION);
  }

  @NotNull
  public synchronized Collection<String> getKeys() {
    try {
      if (myMap == null) {
        return Collections.emptyList();
      }
      else {
        List<String> result = new ArrayList<>();
        myMap.processKeysWithExistingMapping(new CommonProcessors.CollectProcessor<>(result));
        return result;
      }
    }
    catch (IOException e) {
      thingsWentWrongLetsReinitialize(e, "Can't get keys");
      return Collections.emptyList();
    }
  }

  @Nullable
  public synchronized Record getState(String testUrl) {
    try {
      return myMap == null ? null : myMap.get(testUrl);
    }
    catch (IOException e) {
      thingsWentWrongLetsReinitialize(e, "Can't get state for " + testUrl);
      return null;
    }
  }

  public synchronized void removeState(String url) {
    if (myMap != null) {
      try {
        myMap.remove(url);
      }
      catch (IOException e) {
        thingsWentWrongLetsReinitialize(e, "Can't remove state for " + url);
      }
    }
  }

  @Nullable
  public synchronized Map<String, Record> getRecentTests(int limit, Date since) {
    if (myMap == null) return null;

    Map<String, Record> result = new HashMap<>();
    try {
      myMap.processKeysWithExistingMapping(key -> {
        Record record;
        try {
          record = myMap.get(key);
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }

        if (record != null && record.date.compareTo(since) > 0) {
          result.put(key, record);
          if (result.size() >= limit) {
            return false;
          }
        }
        return true;
      });
    }
    catch (IOException e) {
      thingsWentWrongLetsReinitialize(e, "Can't get recent tests");
    }

    return result;
  }

  public synchronized void writeState(@NotNull String testUrl, Record record) {
    if (myMap == null) return;
    try {
      myMap.put(testUrl, record);
    }
    catch (IOException e) {
      thingsWentWrongLetsReinitialize(e, "Can't write state for " + testUrl);
    }
  }

  @Override
  public synchronized void dispose() {
    myMapFlusher.cancel(false);
    myMapFlusher = null;
    if (myMap == null) return;
    try {
      myMap.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      myMap = null;
    }
  }

  private void thingsWentWrongLetsReinitialize(IOException e, String message) {
    try {
      if (myMap != null) {
        try {
          myMap.close();
        }
        catch (IOException ignore) {
        }
        IOUtil.deleteAllFilesStartingWith(myFile);
      }
      myMap = initializeMap();
      LOG.warn(message, e);
    }
    catch (IOException e1) {
      LOG.error("Cannot repair", e1);
      myMap = null;
    }
  }
}
