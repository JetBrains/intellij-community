// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.jetbrains.jps.incremental.storage.HashStorage.HashPerTarget;

public class HashStorage extends AbstractStateStorage<File, HashPerTarget[]> implements StampsStorage<HashStorage.Hash> {
  public static final int MD5_SIZE = 16;
  private final BuildTargetsState myTargetsState;
  private final File myHashesRoot;
  private final MessageDigest md;

  public HashStorage(File dataStorageRoot, BuildTargetsState targetsState) throws IOException {
    super(new File(calcStorageRoot(dataStorageRoot), "data"), new FileKeyDescriptor(), new StateExternalizer());
    myHashesRoot = calcStorageRoot(dataStorageRoot);
    myTargetsState = targetsState;
    try {
      md = MessageDigest.getInstance("MD5");
    }
    catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }

  @NotNull
  private static File calcStorageRoot(File dataStorageRoot) {
    return new File(dataStorageRoot, "hashes");
  }

  @Override
  public File getStorageRoot() {
    return myHashesRoot;
  }

  @Override
  public void force() {
    super.force();
  }

  @Override
  public void clean() throws IOException {
    super.clean();
  }

  @Override
  public void saveStamp(File file, BuildTarget<?> target, Hash stamp) throws IOException {
    int targetId = myTargetsState.getBuildTargetId(target);
    update(file, updateTimestamp(getState(file), targetId, stamp.asBytes()));
  }

  @NotNull
  private static HashPerTarget[] updateTimestamp(HashPerTarget[] oldState, final int targetId, byte[] bytes) {
    final HashPerTarget newItem = new HashPerTarget(targetId, bytes);
    if (oldState == null) {
      return new HashPerTarget[]{newItem};
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
  public void removeStamp(File file, BuildTarget<?> buildTarget) throws IOException { // todo: a full copy, consider to pull up
    HashPerTarget[] state = getState(file);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(buildTarget);
      for (int i = 0; i < state.length; i++) {
        HashPerTarget timestampPerTarget = state[i];
        if (timestampPerTarget.targetId == targetId) {
          if (state.length == 1) {
            remove(file);
          }
          else {
            HashPerTarget[] newState = ArrayUtil.remove(state, i);
            update(file, newState);
            break;
          }
        }
      }
    }
  }

  @Override
  public Hash getStamp(File file, BuildTarget<?> target) throws IOException {
    final HashPerTarget[] state = getState(file);
    if (state != null) {
      int targetId = myTargetsState.getBuildTargetId(target);
      for (HashPerTarget hashPerTarget : state) {
        if (hashPerTarget.targetId == targetId) {
          return Hash.fromBytes(hashPerTarget.hash);
        }
      }
    }
    return Hash.ZERO;
  }

  @Override
  public Hash lastModified(File file) throws IOException {
    // todo: check file size ad ready with buffered reader
    byte[] bytes = Files.readAllBytes(file.toPath());
    byte[] digest = md.digest(bytes);
    return Hash.fromBytes(digest);
  }

  @Override
  public Hash lastModified(File file, @NotNull BasicFileAttributes attrs) throws IOException {
    return lastModified(file);
  }

  static class HashPerTarget {
    public final int targetId;
    public final byte[] hash;

    private HashPerTarget(int targetId, byte[] hash) {
      this.targetId = targetId;
      this.hash = hash;
    }
  }

  static class Hash implements StampsStorage.Stamp {
    static Hash ZERO = new Hash(new byte[]{});

    private final byte[] myBytes;

    Hash(byte[] bytes) {
      myBytes = bytes;
    }

    byte[] asBytes() {
      return myBytes;
    }

    static Hash fromBytes(byte[] bytes) {
      return new Hash(bytes);
    }

    @Override
    public boolean isEqual(StampsStorage.Stamp other) {
      return other instanceof Hash && Arrays.equals(myBytes, ((Hash)other).myBytes);
    }
  }

  private static class StateExternalizer implements DataExternalizer<HashPerTarget[]> {
    @Override
    public void save(@NotNull DataOutput out, HashPerTarget[] value) throws IOException {
      out.writeInt(value.length);
      for (HashPerTarget target : value) {
        out.writeInt(target.targetId);
        out.write(target.hash);
      }
    }

    @Override
    public HashPerTarget[] read(@NotNull DataInput in) throws IOException {
      int size = in.readInt();
      HashPerTarget[] targets = new HashPerTarget[size];
      for (int i = 0; i < size; i++) {
        int id = in.readInt();
        byte[] bytes = new byte[MD5_SIZE];
        in.readFully(bytes);
        targets[i] = new HashPerTarget(id, bytes);
      }
      return targets;
    }
  }
}
