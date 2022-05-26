// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class IndexVersion {
  private static final int BASE_VERSION = 15;
  private static final IndexVersion NON_EXISTING_INDEX_VERSION = new IndexVersion(0, -1, -1);
  private static final int MIN_FS_MODIFIED_TIMESTAMP_RESOLUTION = 2000; // https://en.wikipedia.org/wiki/File_Allocation_Table,
  // 1s for ext3 / hfs+ http://unix.stackexchange.com/questions/11599/determine-file-system-timestamp-accuracy
  // https://en.wikipedia.org/wiki/HFS_Plus
  private static final int OUR_INDICES_TIMESTAMP_INCREMENT = SystemProperties.getIntProperty("idea.indices.timestamp.resolution", 1);

  private static final ConcurrentMap<ID<?, ?>, IndexVersion> ourIndexIdToCreationStamp = new ConcurrentHashMap<>();
  private static volatile int ourVersion = -1;
  private static volatile long ourLastStamp; // ensure any file index stamp increases

  private final long myModificationCount;
  private final int myIndexVersion;
  private final int myCommonIndicesVersion;
  private final long myVfsCreationStamp;

  IndexVersion(long modificationCount, int indexVersion, long vfsCreationStamp) {
    myModificationCount = modificationCount;
    advanceIndexStamp(modificationCount);
    myIndexVersion = indexVersion;
    myCommonIndicesVersion = getVersion();
    myVfsCreationStamp = vfsCreationStamp;
  }

  private static void advanceIndexStamp(long modificationCount) {
    //noinspection NonAtomicOperationOnVolatileField
    ourLastStamp = Math.max(modificationCount, ourLastStamp);
  }

  IndexVersion(DataInput in) throws IOException {
    myIndexVersion = DataInputOutputUtil.readINT(in);
    myCommonIndicesVersion = DataInputOutputUtil.readINT(in);
    myVfsCreationStamp = DataInputOutputUtil.readTIME(in);
    myModificationCount = DataInputOutputUtil.readTIME(in);
    advanceIndexStamp(myModificationCount);
  }

  void write(DataOutput os) throws IOException {
    DataInputOutputUtil.writeINT(os, myIndexVersion);
    DataInputOutputUtil.writeINT(os, myCommonIndicesVersion);
    DataInputOutputUtil.writeTIME(os, myVfsCreationStamp);
    DataInputOutputUtil.writeTIME(os, myModificationCount);
  }

  IndexVersion nextVersion(int indexVersion, long vfsCreationStamp) {
    long modificationCount = calcNextVersion(this == NON_EXISTING_INDEX_VERSION ? ourLastStamp : myModificationCount);
    return new IndexVersion(modificationCount, indexVersion, vfsCreationStamp);
  }

  private static long calcNextVersion(long modificationCount) {
    return Math.max(
      System.currentTimeMillis(),
      Math.max(modificationCount + MIN_FS_MODIFIED_TIMESTAMP_RESOLUTION,
               ourLastStamp + OUR_INDICES_TIMESTAMP_INCREMENT)
    );
  }

  static void initPersistentIndexStamp(DataInput in) throws IOException {
    advanceIndexStamp(DataInputOutputUtil.readTIME(in));
  }

  static void savePersistentIndexStamp(DataOutput out) throws IOException {
    DataInputOutputUtil.writeTIME(out, ourLastStamp);
  }

  private static int getVersion() {
    if (ourVersion == -1) {
      int version = BASE_VERSION;
      for (FileBasedIndexInfrastructureExtension ex : FileBasedIndexInfrastructureExtension.EP_NAME.getExtensions()) {
        int extensionVersion = ex.getVersion();
        // move rocksdb versioning outside
        if (extensionVersion != -1) {
          version = 31 * version + extensionVersion;
        }
      }
      ourVersion = version;
    }
    return ourVersion;
  }

  static void clearCachedIndexVersions() {
    ourVersion = -1;
    ourIndexIdToCreationStamp.clear();
  }

  public static IndexVersionDiff versionDiffers(@NotNull ID<?,?> indexId, int currentIndexVersion) {
    IndexVersion version = getIndexVersion(indexId);
    if (version.myIndexVersion == -1) return new IndexVersionDiff.InitialBuild(currentIndexVersion);

    if (version.myIndexVersion != currentIndexVersion) {
      return new IndexVersionDiff.VersionChanged(version.myIndexVersion, currentIndexVersion, "index version");
    }

    if (version.myCommonIndicesVersion != getVersion()) {
      return new IndexVersionDiff.VersionChanged(version.myCommonIndicesVersion, getVersion(), "common index version");
    }

    long timestamp = FSRecords.getCreationTimestamp();
    if (version.myVfsCreationStamp != timestamp) {
      return new IndexVersionDiff.VersionChanged(version.myVfsCreationStamp, timestamp, "vfs creation stamp");
    }

    return IndexVersionDiff.UP_TO_DATE;
  }

  public static synchronized void rewriteVersion(@NotNull ID<?,?> indexId, final int version) throws IOException {
    if (FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return;
    }

    Path file = IndexInfrastructure.getVersionFile(indexId);
    if (FileBasedIndexImpl.LOG.isDebugEnabled()) {
      FileBasedIndexImpl.LOG.debug("Rewriting " + file + "," + version);
    }
    IndexVersion newIndexVersion = getIndexVersion(indexId).nextVersion(version, FSRecords.getCreationTimestamp());

    if (Files.exists(file)) {
      FileUtil.deleteWithRenaming(file.toFile());
    }
    else {
      Files.createDirectories(file.getParent());
    }
    try (DataOutputStream os = FileUtilRt.doIOOperation(lastAttempt -> {
      try {
        return new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
      }
      catch (IOException ex) {
        if (lastAttempt) {
          throw ex;
        }
        return null;
      }
    })) {
      assert os != null;

      newIndexVersion.write(os);
      ourIndexIdToCreationStamp.put(indexId, newIndexVersion);
    }
  }

  static long getIndexCreationStamp(@NotNull ID<?, ?> indexName) {
    IndexVersion version = getIndexVersion(indexName);
    return version.myModificationCount;
  }

  private static @NotNull IndexVersion getIndexVersion(@NotNull ID<?, ?> indexName) {
    IndexVersion version = ourIndexIdToCreationStamp.get(indexName);
    if (version != null) {
      return version;
    }

    //noinspection SynchronizeOnThis
    synchronized (IndexingStamp.class) {
      version = ourIndexIdToCreationStamp.get(indexName);
      if (version != null) return version;

      try {
        Path versionFile = IndexInfrastructure.getVersionFile(indexName);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(versionFile)))) {
          version = new IndexVersion(in);
          ourIndexIdToCreationStamp.put(indexName, version);
          return version;
        }
      }
      catch (IOException ignore) {
      }
      version = NON_EXISTING_INDEX_VERSION;
      ourIndexIdToCreationStamp.put(indexName, version);
    }
    return version;
  }

  public interface IndexVersionDiff {
    @NotNull
    String getLogText();

    IndexVersionDiff UP_TO_DATE = new IndexVersionDiff() {
      @Override
      public @NotNull String getLogText() {
        return "";
      }
    };

    class InitialBuild implements IndexVersionDiff {
      private final int myVersion;

      public InitialBuild(int version) {myVersion = version;}

      @Override
      public @NotNull String getLogText() {
        return "(v = " + myVersion + ")";
      }
    }

    class CorruptedRebuild implements IndexVersionDiff {
      private final int myVersion;

      public CorruptedRebuild(int version) {myVersion = version;}

      @Override
      public @NotNull String getLogText() {
        return "(corrupted, v = " + myVersion + ")";
      }
    }

    class VersionChanged implements IndexVersionDiff {
      private final long myPreviousVersion;
      private final long myActualVersion;
      private final String myVersionType;

      public VersionChanged(long previousVersion, long actualVersion, String type) {
        myPreviousVersion = previousVersion;
        myActualVersion = actualVersion;
        myVersionType = type;
      }

      @Override
      public @NotNull String getLogText() {
        return "(" + myVersionType + " : " + myPreviousVersion + " -> " + myActualVersion + ")";
      }
    }
  }
}
