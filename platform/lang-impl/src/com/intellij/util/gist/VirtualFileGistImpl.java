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
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.util.SystemProperties.getIntProperty;
import static com.intellij.util.io.IOUtil.KiB;
import static java.nio.file.StandardOpenOption.*;

/** {@link VirtualFileGistOverGistStorage} is a replacement -- keep this class for a while */
class VirtualFileGistImpl<Data> implements VirtualFileGist<Data> {
  private static final Logger LOG = Logger.getInstance(VirtualFileGist.class);

  /**
   * If  > 0: only store in VFS attributes gists <= this size. Store larger gists in dedicated files.
   * If == 0: store all gists in VFS attributes.
   * Value should be <= {@link com.intellij.openapi.vfs.newvfs.persistent.AbstractAttributesStorage#MAX_ATTRIBUTE_VALUE_SIZE}
   */
  public static final int MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES = getIntProperty("idea.gist.max-size-to-store-in-attributes", 50 * KiB);

  private static final String HUGE_GISTS_DIR_NAME = "huge-gists";

  /** `{caches}/huge-gists/{FSRecords.createdTimestamp}/' */
  static final NotNullLazyValue<Path> DIR_FOR_HUGE_GISTS = NotNullLazyValue.atomicLazy(() -> {
    final String vfsStamp = Long.toString(FSRecords.getCreationTimestamp());
    Path gistsDir = Paths.get(FSRecords.getCachesDir(), HUGE_GISTS_DIR_NAME, vfsStamp);
    try {
      if (Files.isRegularFile(gistsDir)) {
        FileUtil.delete(gistsDir);
      }
      Files.createDirectories(gistsDir);
      return gistsDir;
    }
    catch (IOException e) {
      //will be actually thrown in a dedicatedGistFilePath()
      throw new UncheckedIOException("Can't create gists directory [" + gistsDir.toAbsolutePath() + "]", e);
    }
  });

  private static final int INTERNAL_VERSION = 2;

  private static final int VALUE_KIND_NULL = 0;
  private static final int VALUE_KIND_INLINE = 1;
  private static final int VALUE_KIND_IN_DEDICATED_FILE = 2;


  private final @NotNull String id;
  private final int version;
  private final @NotNull GistCalculator<Data> calculator;
  private final @NotNull DataExternalizer<Data> externalizer;

  VirtualFileGistImpl(@NotNull String id,
                      int version,
                      @NotNull DataExternalizer<Data> externalizer,
                      @NotNull GistCalculator<Data> calculator) {
    this.id = id;
    this.version = version;
    this.externalizer = externalizer;
    this.calculator = calculator;
  }

  @Override
  public Data getFileData(@Nullable Project project, @NotNull VirtualFile file) {
    return getOrCalculateAndCache(project, file, calculator).get();
  }

  @Override
  public @Nullable Supplier<Data> getUpToDateOrNull(@Nullable Project project,
                                                    @NotNull VirtualFile file) {
    return getOrCalculateAndCache(project, file, null);
  }

  /** if calculator is null => return cached value, if exists, or null, if not */
  @Contract("_, _, !null -> !null")
  private @Nullable Supplier<Data> getOrCalculateAndCache(@Nullable Project project,
                                                          @NotNull VirtualFile file,
                                                          @Nullable GistCalculator<Data> calculator) {
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

    int gistStamp = GistManagerImpl.getGistStamp(file);

    //attribute content: <gistStamp>, <valueKind>, <gistData>?
    //  gistStamp: varint
    //  valueKind: byte (0,1,2)
    //             0: (gist value is null)
    //             1: (gist value is stored inline, in VFS attribute)
    //                gistData: byte[]
    //             2: (gist value is stored in a dedicated file '<HUGE_GIST_DIR>/<fileId>.<gistId>')

    int cachedGistValueKind = -1;
    try (DataInputStream stream = getFileAttribute(project).readFileAttribute(file)) {
      if (stream != null) {
        int cachedGistStamp = DataInputOutputUtil.readINT(stream);
        cachedGistValueKind = stream.read();
        boolean cachedGistValueIsActual = (cachedGistStamp == gistStamp);
        if (cachedGistValueIsActual) {
          switch (cachedGistValueKind) {
            case VALUE_KIND_NULL -> {
              return () -> null;
            }
            case VALUE_KIND_INLINE -> {
              Data value = externalizer.read(stream);
              return () -> value;
            }
            case VALUE_KIND_IN_DEDICATED_FILE -> {
              Path gistPath = dedicatedGistFilePath(file);
              if (!Files.exists(gistPath)) {
                //looks like data corruption: if gist value was indeed null, we would have stored it as VALUE_KIND_NULL
                //    -> re-calculate gist from scratch
                break;
              }
              try (DataInputStream gistStream = new DataInputStream(Files.newInputStream(gistPath, READ))) {
                Data value = externalizer.read(gistStream);
                return () -> value;
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }

    if (calculator != null) {
      Data value = calculator.calcData(project, file);
      cacheResult(gistStamp, value, project, file,
                  cachedGistValueKind == VALUE_KIND_IN_DEDICATED_FILE);
      return () -> value;
    }
    else {
      return null;
    }
  }

  private void cacheResult(int gistStamp,
                           @Nullable Data result,
                           @Nullable Project project,
                           @NotNull VirtualFile file,
                           boolean wasStoredInDedicatedFileBefore) {
    try (DataOutputStream attributeStream = getFileAttribute(project).writeFileAttribute(file)) {
      DataInputOutputUtil.writeINT(attributeStream, gistStamp);
      if (result == null) {
        attributeStream.writeByte(VALUE_KIND_NULL);
        return;
      }

      if (MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES <= 0) {
        //fast path: avoid temporary intermediate buffers
        attributeStream.writeByte(VALUE_KIND_INLINE);
        externalizer.save(attributeStream, result);
        return;
      }

      //slow path: first save gist into a temporary buffer, check gist size, and decide
      //           there to store the gist content
      BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
      DataOutputStream temporaryStream = new DataOutputStream(outputStream);
      externalizer.save(temporaryStream, result);
      temporaryStream.flush();

      if (outputStream.size() <= MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES) {
        attributeStream.writeByte(VALUE_KIND_INLINE);
        attributeStream.write(outputStream.getInternalBuffer(), 0, outputStream.size());

        if (wasStoredInDedicatedFileBefore) {
          Path gistPath = dedicatedGistFilePath(file);
          FileUtilRt.deleteRecursively(gistPath);
        }
      }
      else {
        attributeStream.writeByte(VALUE_KIND_IN_DEDICATED_FILE);
        Path gistPath = dedicatedGistFilePath(file);
        try (DataOutputStream gistFileStream = new DataOutputStream(Files.newOutputStream(gistPath, WRITE, CREATE))) {
          gistFileStream.write(outputStream.getInternalBuffer(), 0, outputStream.size());
        }
      }
    }
    catch (Throwable e) {
      LOG.error("Can't cache gist[" + id + "]@[" + file + "] -- gist will be re-calculated again on next request", e);
    }
  }

  private static final Map<Pair<String, Integer>, FileAttribute> attributes =
    FactoryMap.create(key -> new FileAttribute(key.first, key.second, false));

  private FileAttribute getFileAttribute(@Nullable Project project) {
    //TODO RC: we create a new VFS attribute for each project -- but never clean up
    //         attributes for projects that are e.g. deleted. 
    synchronized (attributes) {
      return attributes.get(
        Pair.create(id + (project == null ? "###noProject###" : project.getLocationHash()), version + INTERNAL_VERSION));
    }
  }

  @VisibleForTesting
  @NotNull Path dedicatedGistFilePath(@NotNull VirtualFile file) throws IOException {
    String gistFileName = ((VirtualFileWithId)file).getId() + "." + id;
    return DIR_FOR_HUGE_GISTS.get().resolve(gistFileName);
  }

  /**
   * When VFS gets rebuild, {@link FSRecords#getCreationTimestamp()} is changed, so we start to use
   * new {@link #DIR_FOR_HUGE_GISTS} -- but previously used directory is better to be removed at some point.
   * The method finds and removes such old '<caches>/huge-gists/<fsrecords-timestamp>' directories,
   * there {fsrecords-timestamp} != FSRecords.creationTimestamp
   */
  static void cleanupAncientGistsDirs() {
    long currentVFSTimestamp = FSRecords.getCreationTimestamp();
    Path hugeGistsDir = DIR_FOR_HUGE_GISTS.get();
    Path hugeGistsParentDir = hugeGistsDir.getParent();
    LOG.info("Cleaning old huge-gists dirs from [" + hugeGistsParentDir.toAbsolutePath() + "] ...");
    try (var children = Files.list(hugeGistsParentDir)) {
      children
        .filter(Files::isDirectory)
        .filter(dir -> {
          String dirName = dir.getFileName().toString();
          try {
            long dirTimestamp = Long.parseLong(dirName);
            return dirTimestamp != currentVFSTimestamp;
          }
          catch (NumberFormatException e) {
            //intentionally don't remove dirs that looks like not created by us:
            return false;
          }
        })
        .forEach(outdatedHugeGistsDir -> {
          try {
            FileUtilRt.deleteRecursively(outdatedHugeGistsDir);
          }
          catch (IOException e) {
            LOG.info("Can't delete old huge-gists dir [" + outdatedHugeGistsDir.toAbsolutePath() + "]", e);
          }
        });
    }
    catch (IOException e) {
      LOG.info("Can't list huge-gists dir [" + hugeGistsParentDir.toAbsolutePath() + "] children", e);
    }
  }
}

