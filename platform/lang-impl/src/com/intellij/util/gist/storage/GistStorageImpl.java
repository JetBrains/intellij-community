// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.AttributeInputStream;
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.openapi.vfs.newvfs.persistent.VFSAttributesStorage;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.progress.CancellationUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Implementation stores small gists (<= {@link #MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES} in VFS file attributes,
 * and uses dedicated files in a {system}/huge-hists/ folder to store larger gists.
 */
public final class GistStorageImpl extends GistStorage {
  private static final Logger LOG = Logger.getInstance(GistStorageImpl.class);

  /**
   * If  > 0: only store in VFS attributes gists <= this size. Store larger gists in dedicated files.
   * If == 0: store all gists in VFS attributes.
   * Value should be < {@link VFSAttributesStorage#MAX_ATTRIBUTE_VALUE_SIZE}
   */
  @VisibleForTesting
  public static final int MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES = SystemProperties.getIntProperty("idea.gist.max-size-to-store-in-attributes", 50 * IOUtil.KiB);

  private static final String HUGE_GISTS_DIR_NAME = "huge-gists";

  /** `{caches}/huge-gists/{FSRecords.createdTimestamp}/' */
  private static final Supplier<Path> DIR_FOR_HUGE_GISTS = new SynchronizedClearableLazy<>(() -> {
    final String vfsStamp = Long.toString(FSRecords.getCreationTimestamp());
    Path gistsDir = FSRecords.getCacheDir().resolve(HUGE_GISTS_DIR_NAME + "/" + vfsStamp);
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

  private static final Map<String, GistImpl<?>> knownGists = CollectionFactory.createConcurrentWeakValueMap();

  private static final Map<Pair<String, Integer>, FileAttribute> knownAttributes = FactoryMap.create(
    key -> new FileAttribute(key.first, key.second, false)
  );

  public GistStorageImpl() {
    // Gist uses up to 6 bytes for its header, in addition to payload:
    if (MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES > VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE + 6) {
      throw new AssertionError(
        "Configuration mismatch: " +
        "MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES(=" + MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES + ")" +
        " > " +
        "VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE(=" + VFSAttributesStorage.MAX_ATTRIBUTE_VALUE_SIZE + ") + 6");
    }

    //Setup cleanup task for old Gists:
    // remove {caches}/huge-gists/{fsrecords-timestamp} dirs there {fsrecords-timestamp} != FSRecords.getCreatedTimestamp()
    AppExecutorUtil.getAppScheduledExecutorService().schedule(
      GistStorageImpl::cleanupAncientGistsDirs,
      1, TimeUnit.MINUTES
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
      throw new IllegalArgumentException("Gist[" + id + "] already exists, but with version(=" + gist.version() + ") != " + version);
    }
    if (gist.externalizer() != externalizer) {
      throw new IllegalArgumentException(
        "Gist[" + id + "] already exists, but with externalizer(=" + gist.externalizer() + ") != " + externalizer);
    }
    return gist;
  }

  /**
   * When VFS gets rebuild, {@link FSRecords#getCreationTimestamp()} is changed, so we start to use
   * new {@link #DIR_FOR_HUGE_GISTS} -- but previously used directory is better to be removed at some point.
   * The method finds and removes such old '{caches}/huge-gists/{fsrecords-timestamp}' directories,
   * there {fsrecords-timestamp} != FSRecords.creationTimestamp
   */
  private static void cleanupAncientGistsDirs() {
    try {
      FSRecordsImpl vfs = FSRecords.getInstance();
      long currentVFSTimestamp = vfs.getCreationTimestamp();
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
              return dirTimestamp < currentVFSTimestamp;
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
        LOG.info("Cleaning old huge-gists dirs finished");
      }
      catch (IOException e) {
        LOG.info("Can't list huge-gists dir [" + hugeGistsParentDir.toAbsolutePath() + "] children", e);
      }
    }
    catch (AlreadyDisposedException e) {
      LOG.info("Can't cleanup old huge-gists: vfs is disposed -> try next time", e);
    }
  }

  public static final class GistImpl<Data> implements Gist<Data> {
    /**
     * Version of Gist persistent format: must be incremented each time Gist persistent
     * format is changed so that older records can't be read with new code.
     */
    private static final int INTERNAL_VERSION = 1;

    private static final int VALUE_KIND_NULL = 0;
    private static final int VALUE_KIND_INLINE = 1;
    private static final int VALUE_KIND_IN_DEDICATED_FILE = 2;

    private final String id;
    private final int version;
    private final DataExternalizer<Data> externalizer;

    /** Protects put/get operations */
    private final transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

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
    public @NotNull GistData<Data> getProjectData(@Nullable Project project,
                                                  @NotNull VirtualFile file,
                                                  int expectedGistStamp) throws IOException {
      FileAttribute fileAttribute = fileAttributeFor(id, version);
      String projectId = projectIdOf(project);

      CancellationUtil.lockMaybeCancellable(lock.readLock());
      try {
        try (AttributeInputStream stream = fileAttribute.readFileAttribute(file)) {
          if (stream == null) {
            return GistData.empty();
          }
          //iterate through Gists records, look for Gist for the project given:
          while (true) {
            GistRecord<Data> gistRecord = nextRecordOrNull(stream, externalizer);
            if (gistRecord == null) {
              return GistData.empty();
            }
            if (!gistRecord.matchProjectId(projectId)) {
              continue;
            }
            if (gistRecord.gistStamp != expectedGistStamp) {
              return GistData.outdated(gistRecord.gistStamp);
            }
            if (gistRecord.externalFileSuffix == null) {
              return GistData.valid(
                gistRecord.payload,
                gistRecord.gistStamp
              );
            }
            else {
              Path gistPath = dedicatedGistFilePath(file, gistRecord.externalFileSuffix);
              if (!Files.exists(gistPath)) {
                //looks like data corruption: if gist value was indeed null, we would have stored it as VALUE_KIND_NULL
                throw new IOException("Gist file [" + gistPath + "] doesn't exist -> looks like data corruption?");
              }
              try (DataInputStream gistStream = new DataInputStream(Files.newInputStream(gistPath, StandardOpenOption.READ))) {
                return GistData.valid(
                  externalizer.read(gistStream),
                  gistRecord.gistStamp
                );
              }
            }
          }
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Exception e) {
          throw new IOException("Can't read " + this, e);
        }
      }
      finally {
        lock.readLock().unlock();
      }
    }

    @Override
    public void putProjectData(@Nullable Project project,
                               @NotNull VirtualFile file,
                               @Nullable Data data,
                               int gistStamp) throws IOException {
      FileAttribute fileAttribute = fileAttributeFor(id, version);
      String projectId = projectIdOf(project);

      CancellationUtil.lockMaybeCancellable(lock.writeLock());
      try {
        List<GistRecord<Data>> allProjectsGistsRecords = new ArrayList<>();
        try (AttributeInputStream attributeStream = fileAttribute.readFileAttribute(file)) {
          if (attributeStream != null) {
            while (attributeStream.available() > 0) {
              GistRecord<Data> record = nextRecordOrNull(attributeStream, externalizer);
              if (record == null) {
                break;
              }
              allProjectsGistsRecords.add(record);
            }
          }
        }
        //if record for the project already exists -> remove it (will append new one to the end)
        String externalFileSuffix = null;
        for (Iterator<GistRecord<Data>> it = allProjectsGistsRecords.iterator(); it.hasNext(); ) {
          GistRecord<Data> record = it.next();
          if (record.matchProjectId(projectId)) {
            it.remove();
            //to re-use already created file, if any
            externalFileSuffix = record.externalFileSuffix;
          }
        }

        GistRecord<Data> newGistRecord = new GistRecord<>(projectId, gistStamp, data, externalFileSuffix);
        newGistRecord.payloadWasChanged = true;//new payload size is yet to be decided
        allProjectsGistsRecords.add(newGistRecord);

        AttributeOutputStream attributeStream = fileAttribute.writeFileAttribute(file);
        for (GistRecord<Data> record : allProjectsGistsRecords) {
          storeGistRecord(record, file, attributeStream);
        }
        //intentionally NOT using try() construct: we DON'T want to close attributeStream in case of error, because
        // attributeStream is a byte[]-backed stream, which commits its content to the actual file-attributes storage
        // on .close() -- hence without .close() all the partial writes possibly made before the error occurred will
        // be abandoned, which is exactly the desirable behaviour.
        attributeStream.close();
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Exception e) {
        throw new IOException("Can't store gist[" + id + "]@[" + file + "]", e);
      }
      finally {
        lock.writeLock().unlock();
      }
    }

    @Override
    public String toString() {
      return "GistImpl[" + id + "]{version: " + version + ", externalizer: " + externalizer + '}';
    }


    private static @Nullable <T> GistRecord<T> nextRecordOrNull(@NotNull AttributeInputStream stream,
                                                                @NotNull DataExternalizer<T> externalizer) throws IOException {
      if (stream.available() == 0) {
        return null;
      }
      //gist record format: <enumerated projectId:varint>, <gistStamp:int>, <valueKind:byte>, <payload...>?
      //  projectId: varint (enumerated projectId)
      //  gistStamp: int
      //  valueKind: byte (0,1,2)
      //             0: (gist value is null)
      //             1: (gist value is stored inline, in VFS attribute)
      //                payload = gistData: byte[]
      //             2: (gist value is stored in a dedicated file '<HUGE_GIST_DIR>/<fileId>.<gistPathSuffix>')
      //                payload = gistPathSuffix: UTF8 string (as per IOUtil.readUTF)
      String persistedProjectId = stream.readEnumeratedString();
      int persistedGistStamp = stream.readInt();
      int persistedGistValueKind = stream.read();
      switch (persistedGistValueKind) {
        case VALUE_KIND_NULL -> {
          return new GistRecord<>(
            persistedProjectId,
            persistedGistStamp,
            (T)null
          );
        }
        case VALUE_KIND_INLINE -> {
          T gistValue = externalizer.read(stream);
          return new GistRecord<>(
            persistedProjectId,
            persistedGistStamp,
            gistValue
          );
        }
        case VALUE_KIND_IN_DEDICATED_FILE -> {
          String gistPathSuffix = IOUtil.readUTF(stream);
          return new GistRecord<>(
            persistedProjectId,
            persistedGistStamp,
            gistPathSuffix
          );
        }

        case -1 -> {
          if (stream.available() == 0) {
            throw new EOFException("Gist header incomplete: valueKind field is absent");
          }
        }
      }

      throw new IOException("Unrecognized gist.valueKind(=" + persistedGistValueKind + "): incorrect (outdated?) gist format");
    }

    private void storeGistRecord(@NotNull GistRecord<Data> record,
                                 @NotNull VirtualFile file,
                                 @NotNull AttributeOutputStream attributeStream) throws IOException {
      //gist record format: <enumerated projectId:varint>, <gistStamp:int>, <valueKind:byte>, <payload...>?
      //  projectId: varint (enumerated projectId)
      //  gistStamp: int
      //  valueKind: byte (0,1,2)
      //             0: (gist value is null)
      //             1: (gist value is stored inline, in VFS attribute)
      //                payload = gistData: byte[]
      //             2: (gist value is stored in a dedicated file '<HUGE_GIST_DIR>/<fileId>.<gistPathSuffix>')
      //                payload = gistPathSuffix: UTF8 string (as per IOUtil.readUTF)

      attributeStream.writeEnumeratedString(record.projectId);
      attributeStream.writeInt(record.gistStamp);

      String gistFileSuffix = record.externalFileSuffix;

      if (!record.payloadWasChanged) {
        //Fast path for unchanged records -- we already know there to store the payload,
        //     so could skip intermediate buffer and write directly to the attribute
        if (record.payload == null) {
          if (gistFileSuffix != null) {
            //We don't load payload from external files, hence (payload=null & gistFileSuffix!=null)
            // means actual payload is in dedicated file:
            attributeStream.writeByte(VALUE_KIND_IN_DEDICATED_FILE);
            IOUtil.writeUTF(attributeStream, gistFileSuffix);
          }
          else {
            attributeStream.writeByte(VALUE_KIND_NULL);
          }
        }
        else {
          if (record.externalFileSuffix == null) {
            attributeStream.writeByte(VALUE_KIND_INLINE);
            externalizer.save(attributeStream, record.payload);
          }
          else {
            //We do not deserialize payload if it is in an external file, hence if .externalFileSuffix!=null
            //  .payload must be null then
            throw new AssertionError("Bug " + record + ": unchanged record with .payload!=null must have .externalFileSuffix=null");
          }
        }
        return;
      }

      //Changed record with payload=null: fast path
      if (record.payload == null) {
        attributeStream.writeByte(VALUE_KIND_NULL);
        //if huge-file was used previously -> remove the file
        if (gistFileSuffix != null) {
          Path gistPath = dedicatedGistFilePath(file, gistFileSuffix);
          FileUtilRt.deleteRecursively(gistPath);
        }
        return;
      }

      //Changed record with payload!=null: serialise payload into a temporary buffer, check buffer size,
      // and decide there to store payload of that size -- inline in attribute, or in a dedicated file:

      BufferExposingByteArrayOutputStream outputStream = new BufferExposingByteArrayOutputStream();
      DataOutputStream temporaryStream = new DataOutputStream(outputStream);
      externalizer.save(temporaryStream, record.payload);
      temporaryStream.flush();

      if (outputStream.size() <= MAX_GIST_SIZE_TO_STORE_IN_ATTRIBUTES) {
        attributeStream.writeByte(VALUE_KIND_INLINE);
        attributeStream.write(outputStream.getInternalBuffer(), 0, outputStream.size());

        //payload is small enough to inline it: if huge-file was used previously -> remove the file
        if (gistFileSuffix != null) {
          Path gistPath = dedicatedGistFilePath(file, gistFileSuffix);
          FileUtilRt.deleteRecursively(gistPath);
        }
      }
      else {
        attributeStream.writeByte(VALUE_KIND_IN_DEDICATED_FILE);
        if (gistFileSuffix == null) {
          //lets hope random UUID is random enough for avoiding collisions
          gistFileSuffix = UUID.randomUUID().toString();
        }
        IOUtil.writeUTF(attributeStream, gistFileSuffix);

        Path gistPath = dedicatedGistFilePath(file, gistFileSuffix);
        try (DataOutputStream gistFileStream = new DataOutputStream(Files.newOutputStream(gistPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE))) {
          gistFileStream.write(outputStream.getInternalBuffer(), 0, outputStream.size());
        }
      }
    }


    @VisibleForTesting
    @NotNull Path dedicatedGistFilePath(@NotNull VirtualFile file,
                                        @Nullable String gistPathSuffix) throws UncheckedIOException {
      String gistFileName = ((VirtualFileWithId)file).getId() + "." + gistPathSuffix;
      return DIR_FOR_HUGE_GISTS.get().resolve(gistFileName);
    }

    private static @NotNull String projectIdOf(@Nullable Project project) {
      return project == null ? "" : project.getLocationHash();
    }

    private static FileAttribute fileAttributeFor(@NotNull @NonNls String id,
                                                  int version) {
      synchronized (knownAttributes) {
        return knownAttributes.get(
          Pair.create(id, version + INTERNAL_VERSION)
        );
      }
    }
  }

  private static final class GistRecord<T> {
    private final @NotNull String projectId;
    private final int gistStamp;

    private final @Nullable T payload;
    private final @Nullable String externalFileSuffix;

    private boolean payloadWasChanged = false;

    private GistRecord(@NotNull String projectId,
                       int gistStamp,
                       @Nullable T payload) {
      this(projectId, gistStamp, payload, null);
    }

    private GistRecord(@NotNull String projectId,
                       int gistStamp,
                       @NotNull String externalFileSuffix) {
      this(projectId, gistStamp, null, externalFileSuffix);
    }

    private GistRecord(@NotNull String projectId,
                       int gistStamp,
                       @Nullable T payload,
                       @Nullable String externalFileSuffix) {
      this.projectId = projectId;
      this.gistStamp = gistStamp;
      this.payload = payload;
      this.externalFileSuffix = externalFileSuffix;
    }

    public boolean matchProjectId(@NotNull String projectId) {
      return this.projectId.equals(projectId);
    }


    @Override
    public String toString() {
      return "GistRecord[" +
             "projectId='" + projectId + '\'' +
             ", gistStamp=" + gistStamp +
             ", payload=" + payload +
             ", externalFileSuffix='" + externalFileSuffix + '\'' +
             ", payloadSizeIsUnknown=" + payloadWasChanged +
             ']';
    }
  }
}
