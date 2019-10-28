// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.jetbrains.jps.incremental.storage.FileStampStorage.FileStamp;
import static org.jetbrains.jps.incremental.storage.FileStampStorage.HashStampPerTarget;
import static org.jetbrains.jps.incremental.storage.FileTimestampStorage.Timestamp;

public class FileStampStorage extends AbstractStateStorage<String, HashStampPerTarget[]> implements TimestampStorage<FileStamp> {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final String HASH_FUNCTION = "MD5";
  private static final byte CARRIAGE_RETURN_CODE = 13;
  private static final byte LINE_FEED_CODE = 10;
  private static final int HASH_FUNCTION_SIZE = 16;
  private final FileTimestampStorage myTimestampStorage;
  private final PathRelativizerService myRelativizer;
  private final BuildTargetsState myTargetsState;
  private final File myFileStampRoot;

  public FileStampStorage(File dataStorageRoot, PathRelativizerService relativizer, BuildTargetsState targetsState) throws IOException {
    super(new File(calcStorageRoot(dataStorageRoot), "data"), ProjectStamps.PORTABLE_CACHES ? new PortablePathStringDescriptor() : new PathStringDescriptor(),
          new StateExternalizer());
    myTimestampStorage = new FileTimestampStorage(dataStorageRoot, targetsState);
    myFileStampRoot = calcStorageRoot(dataStorageRoot);
    myRelativizer = relativizer;
    myTargetsState = targetsState;
  }

  @NotNull
  private String relativePath(@NotNull File file) {
    return myRelativizer.toRelative(file.getAbsolutePath());
  }

  @NotNull
  private static File calcStorageRoot(File dataStorageRoot) {
    return new File(dataStorageRoot, "hashes");
  }

  @Override
  public File getStorageRoot() {
    return myFileStampRoot;
  }

  @Override
  public void saveStamp(File file, BuildTarget<?> buildTarget, FileStamp stamp) throws IOException {
    myTimestampStorage.saveStamp(file, buildTarget, Timestamp.fromLong(stamp.myTimestamp));
    int targetId = myTargetsState.getBuildTargetId(buildTarget);
    String path = relativePath(file);
    update(path, updateFilesStamp(getState(path), targetId, stamp));
  }

  @NotNull
  private static HashStampPerTarget[] updateFilesStamp(HashStampPerTarget[] oldState, final int targetId, FileStamp stamp) {
    final HashStampPerTarget newItem = new HashStampPerTarget(targetId, stamp.myBytes);
    if (oldState == null) {
      return new HashStampPerTarget[]{newItem};
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
    myTimestampStorage.removeStamp(file, buildTarget);
    String path = relativePath(file);
    HashStampPerTarget[] state = getState(path);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(buildTarget);
      for (int i = 0; i < state.length; i++) {
        if (state[i].targetId == targetId) {
          if (state.length == 1) {
            remove(path);
          }
          else {
            HashStampPerTarget[] newState = ArrayUtil.remove(state, i);
            update(path, newState);
            break;
          }
        }
      }
    }
  }

  @Override
  public FileStamp getPreviousStamp(File file, BuildTarget<?> target) throws IOException {
    Timestamp previousTimestamp = myTimestampStorage.getPreviousStamp(file, target);
    HashStampPerTarget[] state = getState(relativePath(file));
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (HashStampPerTarget filesStampPerTarget : state) {
        if (filesStampPerTarget.targetId == targetId) {
          return new FileStamp(filesStampPerTarget.hash, previousTimestamp.asLong());
        }
      }
    }
    return FileStamp.EMPTY;
  }

  @Override
  public FileStamp getCurrentStamp(File file) throws IOException {
    Timestamp currentTimestamp = myTimestampStorage.getCurrentStamp(file);
    return new FileStamp(getFileHash(file), currentTimestamp.asLong());
  }

  private static byte[] getFileHash(@NotNull File file) throws IOException {
    MessageDigest md = getMessageDigest();
    md.reset();
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] buffer = new byte[1024 * 1024];
      int length;
      while ((length = fis.read(buffer)) != -1) {
        byte[] result = new byte[length];
        int copiedBytes = 0;
        for (int i = 0; i < length; i++) {
          if (buffer[i] != CARRIAGE_RETURN_CODE && ((i + 1) >= length || buffer[i + 1] != LINE_FEED_CODE)) {
            result[copiedBytes] = buffer[i];
            copiedBytes++;
          }
        }
        md.update(copiedBytes != result.length ? Arrays.copyOf(result, result.length - (result.length - copiedBytes)) : result);
      }
    }
    return md.digest();
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
  public boolean isDirtyStamp(@NotNull Stamp stamp, File file) throws IOException {
    if (!(stamp instanceof FileStamp)) return true;
    FileStamp filesStamp = (FileStamp)stamp;
    if (!myTimestampStorage.isDirtyStamp(Timestamp.fromLong(filesStamp.myTimestamp), file)) return false;
    return !Arrays.equals(filesStamp.myBytes, getFileHash(file));
  }

  @Override
  public boolean isDirtyStamp(Stamp stamp, File file, @NotNull BasicFileAttributes attrs) throws IOException {
    if (!(stamp instanceof FileStamp)) return true;
    FileStamp filesStamp = (FileStamp)stamp;
    if (!myTimestampStorage.isDirtyStamp(Timestamp.fromLong(filesStamp.myTimestamp), file, attrs)) return false;
    return !Arrays.equals(filesStamp.myBytes, getFileHash(file));
  }

  @Override
  public void force() {
    super.force();
    myTimestampStorage.force();
  }

  @Override
  public void clean() throws IOException {
    super.clean();
    myTimestampStorage.clean();
  }

  @Override
  public boolean wipe() {
    return super.wipe() && myTimestampStorage.wipe();
  }

  @Override
  public void close() throws IOException {
    super.close();
    myTimestampStorage.close();
  }

  static class HashStampPerTarget {
    public final int targetId;
    public final byte[] hash;

    private HashStampPerTarget(int targetId, byte[] hash) {
      this.targetId = targetId;
      this.hash = hash;
    }
  }

  static class FileStamp implements StampsStorage.Stamp {
    static FileStamp EMPTY = new FileStamp(new byte[]{}, -1L);

    private final byte[] myBytes;
    private final long myTimestamp;

    private FileStamp(byte[] bytes, long timestamp) {
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

  private static class StateExternalizer implements DataExternalizer<HashStampPerTarget[]> {
    @Override
    public void save(@NotNull DataOutput out, HashStampPerTarget[] value) throws IOException {
      out.writeInt(value.length);
      for (HashStampPerTarget target : value) {
        out.writeInt(target.targetId);
        out.write(target.hash);
      }
    }

    @Override
    public HashStampPerTarget[] read(@NotNull DataInput in) throws IOException {
      int size = in.readInt();
      HashStampPerTarget[] targets = new HashStampPerTarget[size];
      for (int i = 0; i < size; i++) {
        int id = in.readInt();
        byte[] bytes = new byte[HASH_FUNCTION_SIZE];
        in.readFully(bytes);
        targets[i] = new HashStampPerTarget(id, bytes);
      }
      return targets;
    }
  }
}
