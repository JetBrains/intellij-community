// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist.storage;

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
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NonNls;
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

import static com.intellij.util.SystemProperties.getIntProperty;
import static com.intellij.util.io.IOUtil.KiB;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Implementation stores small gists (<= {@link #MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES} in a VFS file attributes,
 * and uses dedicated files in a {system}/huge-hists/ folder to store larger gists.
 */
public class GistStorageImpl extends GistStorage {
  private static final Logger LOG = Logger.getInstance(GistStorage.class);

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

  private static final Map<String, GistImpl<?>> knownGists = ContainerUtil.createConcurrentWeakValueMap();

  public GistStorageImpl() {
    //Setup cleanup task for old Gists:
    // remove <caches>/huge-gists/<fsrecords-timestamp> dirs there <fsrecords-timestamp> != FSRecords.getCreatedTimestamp()
    AppExecutorUtil.getAppScheduledExecutorService().schedule(
      GistStorageImpl::cleanupAncientGistsDirs,
      15, SECONDS
    );
  }

  @Override
  public @NotNull <Data> Gist<Data> newGist(@NotNull String id,
                                            int version,
                                            @NotNull DataExternalizer<Data> externalizer) {
    @SuppressWarnings("unchecked")
    GistImpl<Data> gist = (GistImpl<Data>)knownGists.computeIfAbsent(
      id,
      __ -> new GistImpl<>(id, version, externalizer)
    );

    if (gist.version() != version) {
      throw new IllegalArgumentException("Gist[" + id + "] is already exists, but with version(=" + gist.version() + ") != " + version);
    }
    if (gist.externalizer() != externalizer) {
      throw new IllegalArgumentException(
        "Gist[" + id + "] is already exists, but with externalizer(=" + gist.externalizer() + ") != " + externalizer);
    }
    return gist;
  }

  /**
   * When VFS gets rebuild, {@link FSRecords#getCreationTimestamp()} is changed, so we start to use
   * new {@link #DIR_FOR_HUGE_GISTS} -- but previously used directory is better to be removed at some point.
   * The method finds and removes such old '<caches>/huge-gists/<fsrecords-timestamp>' directories,
   * there {fsrecords-timestamp} != FSRecords.creationTimestamp
   */
  private static void cleanupAncientGistsDirs() {
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

  private static final Map<Pair<String, Integer>, FileAttribute> attributes =
    FactoryMap.create(key -> new FileAttribute(key.first, key.second, false));

  private static FileAttribute lookupFileAttribute(@Nullable Project project,
                                                   @NotNull @NonNls String id,
                                                   int version) {
    //TODO RC: we create a new VFS attribute for each project -- but never clean up
    //         attributes for projects that are e.g. deleted.
    synchronized (attributes) {
      return attributes.get(
        Pair.create(id + (project == null ? "###noProject###" : project.getLocationHash()), version));
    }
  }

  public static class GistImpl<Data> implements Gist<Data> {
    private static final int VALUE_KIND_NULL = 0;
    private static final int VALUE_KIND_INLINE = 1;
    private static final int VALUE_KIND_IN_DEDICATED_FILE = 2;

    private final String id;
    private final int version;
    private final DataExternalizer<Data> externalizer;

    public GistImpl(@NotNull @NonNls String id,
                    int version,
                    @NotNull DataExternalizer<Data> externalizer) {
      this.id = id;
      this.externalizer = externalizer;
      this.version = version;
    }

    @Override
    public @NotNull String id() {
      return id;
    }

    public @NotNull DataExternalizer<Data> externalizer() {
      return externalizer;
    }

    @Override
    public int version() {
      return version;
    }

    @Override
    public GistData<Data> getProjectData(@Nullable Project project,
                                         @NotNull VirtualFile file,
                                         int expectedGistStamp) throws IOException {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      ProgressManager.checkCanceled();

      try (DataInputStream stream = lookupFileAttribute(project, id, version).readFileAttribute(file)) {
        if (stream == null) {
          return GistData.noData();
        }
        int persistedGistStamp = DataInputOutputUtil.readINT(stream);
        int persistedGistValueKind = stream.read();
        boolean persistedGistValueIsActual = (persistedGistStamp == expectedGistStamp);
        if (!persistedGistValueIsActual) {
          return GistData.outdated(persistedGistStamp);
        }
        switch (persistedGistValueKind) {
          case VALUE_KIND_NULL -> {
            return GistData.withData(
              null,
              persistedGistStamp
            );
          }
          case VALUE_KIND_INLINE -> {
            return GistData.withData(
              externalizer.read(stream),
              persistedGistStamp
            );
          }
          case VALUE_KIND_IN_DEDICATED_FILE -> {
            Path gistPath = dedicatedGistFilePath(file);
            if (!Files.exists(gistPath)) {
              //looks like data corruption: if gist value was indeed null, we would have stored it as VALUE_KIND_NULL
              throw new IOException("Gist file [" + gistPath + "] doesn't exists -> corruption?");
            }
            try (DataInputStream gistStream = new DataInputStream(Files.newInputStream(gistPath, READ))) {
              return GistData.withData(
                externalizer.read(gistStream),
                persistedGistStamp
              );
            }
          }

          default ->
            throw new IOException("Unrecognized gist.valueKind(=" + persistedGistValueKind + "): incorrect (outdated?) gist format");
        }
      }
      catch (IOException e) {
        throw new IOException("Can't read " + this, e);
      }
    }

    @Override
    public void putProjectData(@Nullable Project project,
                               @NotNull VirtualFile file,
                               @Nullable Data data,
                               int gistStamp) throws IOException {
      ApplicationManager.getApplication().assertWriteAccessAllowed();
      ProgressManager.checkCanceled();

      //attribute content: <valueKind>, <gistData>?
      //  valueKind: byte (0,1,2)
      //             0: (gist value is null)
      //             1: (gist value is stored inline, in VFS attribute)
      //                gistData: byte[]
      //             2: (gist value is stored in a dedicated file '<HUGE_GIST_DIR>/<fileId>.<gistId>')
      //                MAYBE: put <gistId> here also?

      FileAttribute fileAttribute = lookupFileAttribute(project, id, version);
      try (DataOutputStream attributeStream = fileAttribute.writeFileAttribute(file)) {
        DataInputOutputUtil.writeINT(attributeStream, gistStamp);
        if (data == null) {
          attributeStream.writeByte(VALUE_KIND_NULL);
          return;
        }

        if (MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES <= 0) {
          //fast path: avoid temporary intermediate buffers
          attributeStream.writeByte(VALUE_KIND_INLINE);
          externalizer.save(attributeStream, data);
          return;
        }

        //slow path: first save gist into a temporary buffer, check gist size, and decide
        //           there to store the gist content
        BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
        DataOutputStream temporaryStream = new DataOutputStream(outputStream);
        externalizer.save(temporaryStream, data);
        temporaryStream.flush();

        if (outputStream.size() <= MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES) {
          attributeStream.writeByte(VALUE_KIND_INLINE);
          attributeStream.write(outputStream.getInternalBuffer(), 0, outputStream.size());

          //TODO RC: if use previously huge-file -> remove it
          //         Maybe clean up outdated files on app.exit? I.e. scan knownGists, and check
          //         do they still need huge files?
          //if (wasStoredInDedicatedFileBefore) {
          //  Path gistPath = dedicatedGistFilePath(file);
          //  FileUtilRt.deleteRecursively(gistPath);
          //}
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
        throw new IOException("Can't cache gist[" + id + "]@[" + file + "] -- gist will be re-calculated again on next request", e);
      }
    }

    @VisibleForTesting
    @NotNull Path dedicatedGistFilePath(@NotNull VirtualFile file) throws IOException {
      String gistFileName = ((VirtualFileWithId)file).getId() + "." + id;
      return DIR_FOR_HUGE_GISTS.get().resolve(gistFileName);
    }

    @Override
    public String toString() {
      return "GistImpl[" + id + "]{version: " + version + ", externalizer: " + externalizer + '}';
    }
  }
}
