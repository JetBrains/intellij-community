// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.jetbrains.jps.incremental.storage.FileStampStorage.FileStamp;
import static org.jetbrains.jps.incremental.storage.FileStampStorage.FileStampPerTarget;

public class FileStampStorage extends AbstractStateStorage<String, FileStampPerTarget[]> implements TimestampStorage<FileStamp> {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final String HASH_FUNCTION = "MD5";
  private static final int HASH_FUNCTION_SIZE = 16;
  private final PathRelativizerService myRelativizer;
  private final BuildTargetsState myTargetsState;
  private final File myFileStampRoot;

  public FileStampStorage(File dataStorageRoot, PathRelativizerService relativizer, BuildTargetsState targetsState) throws IOException {
    super(new File(calcStorageRoot(dataStorageRoot), "data"), new PathStringDescriptor(), new FileStampStorage.StateExternalizer());
    myFileStampRoot = calcStorageRoot(dataStorageRoot);
    myRelativizer = relativizer;
    myTargetsState = targetsState;
  }

  @NotNull
  private String relativePath(@NotNull File file) {
    return myRelativizer.toRelative(file.getPath());
  }

  @NotNull
  private static File calcStorageRoot(File dataStorageRoot) {
    return new File(dataStorageRoot, "file-stamps");
  }

  @Override
  public File getStorageRoot() {
    return myFileStampRoot;
  }

  @Override
  public void saveStamp(File file, BuildTarget<?> buildTarget, FileStamp stamp) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(buildTarget);
    String path = relativePath(file);
    update(path, updateFilesStamp(getState(path), targetId, stamp));
  }

  @NotNull
  private static FileStampPerTarget[] updateFilesStamp(FileStampPerTarget[] oldState, final int targetId, FileStamp stamp) {
    final FileStampPerTarget newItem = new FileStampPerTarget(targetId, stamp.myBytes, stamp.myTimestamp);
    if (oldState == null) {
      return new FileStampPerTarget[]{newItem};
    }
    for (int i = 0, length = oldState.length; i < length; i++) {
      if (oldState[i].targetId == targetId) {
        oldState[i] = newItem;
        return oldState;
      }
    }
    return ArrayUtil.append(oldState, newItem);
  }

  @Override
  public void removeStamp(File file, BuildTarget<?> buildTarget) throws IOException {
    String path = relativePath(file);
    FileStampPerTarget[] state = getState(path);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(buildTarget);
      for (int i = 0; i < state.length; i++) {
        if (state[i].targetId == targetId) {
          if (state.length == 1) {
            remove(path);
          }
          else {
            FileStampPerTarget[] newState = ArrayUtil.remove(state, i);
            update(path, newState);
            break;
          }
        }
      }
    }
  }

  @Override
  public FileStamp getPreviousStamp(File file, BuildTarget<?> target) throws IOException {
    FileStampPerTarget[] state = getState(relativePath(file));
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (FileStampPerTarget filesStampPerTarget : state) {
        if (filesStampPerTarget.targetId == targetId) {
          return new FileStamp(filesStampPerTarget.hash, filesStampPerTarget.timestamp);
        }
      }
    }
    return FileStamp.EMPTY;
  }

  @Override
  public FileStamp getCurrentStamp(File file) throws IOException {
    long lastModified = FSOperations.lastModified(file);
    return new FileStamp(getFileHash(file), lastModified);
  }

  private static byte[] getFileHash(@NotNull File file) throws IOException {
    byte[] bytes = Files.readAllBytes(file.toPath());
    return getMessageDigest().digest(bytes);
  }

  @NotNull
  private static MessageDigest getMessageDigest() throws IOException {
    MessageDigest messageDigest = MESSAGE_DIGEST_THREAD_LOCAL.get();
    if (messageDigest != null) return messageDigest;
    try {
      messageDigest = MessageDigest.getInstance(HASH_FUNCTION);
      MESSAGE_DIGEST_THREAD_LOCAL.set(messageDigest);
      return messageDigest;
    }
    catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }

  @Override
  public boolean isDirtyStamp(@NotNull Stamp stamp, File file) {
    if (!(stamp instanceof FileStamp)) return true;
    FileStamp filesStamp = (FileStamp) stamp;
    long lastModified = FSOperations.lastModified(file);
    return filesStamp.myTimestamp != lastModified;
  }

  @Override
  public boolean isDirtyStamp(Stamp stamp, File file, @NotNull BasicFileAttributes attrs) {
    if (!(stamp instanceof FileStamp)) return true;
    FileStamp filesStamp = (FileStamp) stamp;
    if (attrs.isRegularFile()) {
      return filesStamp.myTimestamp != attrs.lastModifiedTime().toMillis();
    }
    return isDirtyStamp(stamp,file);
  }

  static class FileStampPerTarget {
    public final int targetId;
    public final byte[] hash;
    public final long timestamp;

    private FileStampPerTarget(int targetId, byte[] hash, long timestamp) {
      this.targetId = targetId;
      this.hash = hash;
      this.timestamp = timestamp;
    }
  }

  static class FileStamp implements StampsStorage.Stamp {
    static FileStamp EMPTY = new FileStamp(new byte[]{}, -1L);

    private final byte[] myBytes;
    private final long myTimestamp;

    FileStamp(byte[] bytes, long timestamp) {
      myBytes = bytes;
      myTimestamp = timestamp;
    }

    @Override
    public String toString() {
      return "FileStamp{" +
             "myBytes=" + Arrays.toString(myBytes) +
             ", myTimestamp=" + myTimestamp +
             '}';
    }
  }

  private static class StateExternalizer implements DataExternalizer<FileStampPerTarget[]> {
    @Override
    public void save(@NotNull DataOutput out, FileStampPerTarget[] value) throws IOException {
      out.writeInt(value.length);
      for (FileStampPerTarget target : value) {
        out.writeInt(target.targetId);
        out.writeLong(target.timestamp);
        out.write(target.hash);
      }
    }

    @Override
    public FileStampPerTarget[] read(@NotNull DataInput in) throws IOException {
      int size = in.readInt();
      FileStampPerTarget[] targets = new FileStampPerTarget[size];
      for (int i = 0; i < size; i++) {
        int id = in.readInt();
        long timestamp = in.readLong();
        byte[] bytes = new byte[HASH_FUNCTION_SIZE];
        in.readFully(bytes);
        targets[i] = new FileStampPerTarget(id, bytes, timestamp);
      }
      return targets;
    }
  }
}
