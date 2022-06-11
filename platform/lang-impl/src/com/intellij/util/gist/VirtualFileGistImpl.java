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
package com.intellij.util.gist;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * @author peter
 */
class VirtualFileGistImpl<Data> implements VirtualFileGist<Data> {
  private static final Logger LOG = Logger.getInstance(VirtualFileGist.class);
  private static final int ourInternalVersion = 2;
  static final Key<AtomicInteger> GIST_INVALIDATION_COUNT_KEY = Key.create("virtual.file.gist.invalidation.count");

  @NotNull private final String myId;
  private final int myVersion;
  @NotNull private final GistCalculator<Data> myCalculator;
  @NotNull private final DataExternalizer<Data> myExternalizer;

  VirtualFileGistImpl(@NotNull String id, int version, @NotNull DataExternalizer<Data> externalizer, @NotNull GistCalculator<Data> calcData) {
    myId = id;
    myVersion = version;
    myExternalizer = externalizer;
    myCalculator = calcData;
  }

  @Override
  public Data getFileData(@Nullable Project project, @NotNull VirtualFile file) {
    return getOrCalculateAndCache(project, file, myCalculator).get();
  }

  @Override
  public @Nullable Supplier<Data> getUpToDateOrNull(@Nullable Project project, @NotNull VirtualFile file) {
    return getOrCalculateAndCache(project, file, null);
  }

  @Contract("_, _, !null -> !null")
  private @Nullable Supplier<Data> getOrCalculateAndCache(@Nullable Project project, @NotNull VirtualFile file, @Nullable GistCalculator<Data> calculator) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    ProgressManager.checkCanceled();

    if (!(file instanceof VirtualFileWithId)) {
      if (calculator != null) {
        Data value = calculator.calcData(project, file);
        return () -> value;
      }
      else {
        return null;
      }
    }

    AtomicInteger invalidationCount = file.getUserData(GIST_INVALIDATION_COUNT_KEY);
    int stamp = Objects.hash(file.getModificationCount(),
                             ((GistManagerImpl)GistManager.getInstance()).getReindexCount(),
                             invalidationCount != null ? invalidationCount.get() : 0);

    try (DataInputStream stream = getFileAttribute(project).readFileAttribute(file)) {
      if (stream != null && DataInputOutputUtil.readINT(stream) == stamp) {
        Data value = stream.readBoolean() ? myExternalizer.read(stream) : null;
        return () -> value;
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    if (calculator != null) {
      Data value = calculator.calcData(project, file);
      cacheResult(stamp, value, project, file);
      return () -> value;
    }
    else {
      return null;
    }
  }

  private void cacheResult(int modCount, @Nullable Data result, Project project, VirtualFile file) {
    try (DataOutputStream out = getFileAttribute(project).writeFileAttribute(file)) {
      DataInputOutputUtil.writeINT(out, modCount);
      out.writeBoolean(result != null);
      if (result != null) {
        myExternalizer.save(out, result);
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  private static final Map<Pair<String, Integer>, FileAttribute> ourAttributes =
    FactoryMap.create(key -> new FileAttribute(key.first, key.second, false));

  private FileAttribute getFileAttribute(@Nullable Project project) {
    synchronized (ourAttributes) {
      return ourAttributes.get(Pair.create(myId + (project == null ? "###noProject###" : project.getLocationHash()), myVersion + ourInternalVersion));
    }
  }
}

