// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist.storage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.gist.VirtualFileGist;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Service allows attaching named blobs ('gists') to a {@link VirtualFile}.
 * <p>
 * It is a direct analog of VFS file attributes, but VFS attributes are considered very low-level,
 * and have some implementation limitations (e.g. max size) -- and generally we want to limit VFS
 * file attributes use outside the core platform code. This service is designed to be used instead
 * of VFS file attributes, from 'middle level' code.
 * <p>
 * <dl>
 *   <dt>Performance:</dt>
 *   <dd>Every call to {@link Gist#getProjectData(Project, VirtualFile, int)} and {@link Gist#getGlobalData(VirtualFile, int)} likely
 *   means <b>disk access</b> and {@link DataExternalizer#read(DataInput)} call. I.e. {@link GistStorage} doesn't
 *   cache gists values in memory. If you access gists frequently -- you should take care of proper caching.</dd>
 *   <dt>Locking:</dt>
 *   <dd>{@link GistStorage} and {@link Gist} themselves do not require any locking, nor read-write action.
 *  But externalizer <i>could</i> require locking, and it is up to the caller to ensure apt locks are acquired.</dd>
 *   <dt>Versioning:</dt>
 *   <dd>{@link Gist} has a 'version' -- think of it as a <i>version of a binary format</i> used.
 *   Also, gist's data <i>value</i> could be assigned a 'stamp' -- think of it as a version of current <i>value</i>.
 *   I.e. you store value with specific .stamp, and read value expected .stamp given -- and if the expected
 *   stamp is not the same as was really stored along with the value -- then you get 'no value' back.
 *   (If you don't need to track value stamps -- you could just always use stamp=0)</dd>
 *   <dt>Persistence:</dt>
 *   <dd>It is <i>not guaranteed</i> that stored values always could be read back: underlying storage (VFS) could be
 *   rebuild from 0 due to corruption, or implementation changes, and all stored values is lost in such cases.
 *   Client code must be always ready to re-create Gist value if absent. </dd>
 * </dl>
 * <p>
 * ({@link GistStorage} is initially created for storing {@link VirtualFileGist}, hence some API peculiarities,
 * and hence it is marked as @Internal)
 * <p>
 * If you're about to use {@link GistStorage} service to persist some file-associated computation -- please,
 * consider {@link VirtualFileGist} first: likely {@link VirtualFileGist} fits better, and it is more
 * high-level API.
 *
 * @see VirtualFileGist
 */
@ApiStatus.Internal
public abstract class GistStorage {
  public static @NotNull GistStorage getInstance() {
    return ApplicationManager.getApplication().getService(GistStorage.class);
  }

  /**
   * Create a new {@link Gist}.
   *
   * @param id           a unique identifier of this gist
   * @param version      should be incremented each time the {@code externalizer} logic changes.
   * @param externalizer used to store the data to the disk and retrieve it
   * @param <Data>       the type of the data to cache
   * @return the gist object, where {@link Gist#getGlobalData}/{@link Gist#getProjectData(Project, VirtualFile, int)} can
   * later be used to retrieve the data stored
   */
  public abstract @NotNull <Data> Gist<Data> newGist(@NotNull @NonNls String id,
                                            int version,
                                            @NotNull DataExternalizer<Data> externalizer);

  public interface Gist<Data> {

    @NotNull String id();

    int version();

    /**
     * project = null means data is not attached to any specific project,
     * i.e. it is global (application-wise) data
     */
    @NotNull GistData<Data> getProjectData(@Nullable Project project,
                                           @NotNull VirtualFile file,
                                           int expectedGistStamp) throws IOException;

    default @NotNull GistData<Data> getGlobalData(@NotNull VirtualFile file,
                                                  int expectedGistStamp) throws IOException {
      return getProjectData(null, file, expectedGistStamp);
    }

    /**
     * project = null means data is not attached to any specific project,
     * i.e. it is global (application-wise) data
     */
    void putProjectData(@Nullable Project project,
                        @NotNull VirtualFile file,
                        @Nullable Data data,
                        int gistStamp) throws IOException;

    default void putGlobalData(@NotNull VirtualFile file,
                               @Nullable Data data,
                               int gistStamp) throws IOException {
      putProjectData(null, file, data, gistStamp);
    }
  }

  /**
   * GistData is basically a union:
   * <ol>
   *   <li>
   *     <b>valid</b>: hasData=true, data=(whatever data Gist really has, including null), stamp=(whatever stamp Gist has)
   *   </li>
   *   <li>
   *     <b>outdated</b>: hasData=false, stamp=(whatever stamp Gist has), data=null
   *   </li>
   *   <li>
   *     <b>empty</b>: hasData=false, stamp=NULL_STAMP, data=null
   *   </li>
   * </ol>
   * For the most use-cases 'outdated' and 'empty' are indistinguishable, so it is enough to check
   * {@link #hasData()}, or just use methods like {@link #dataIfExists()}, {@link #dataOr(Object)}
   */
  public static final class GistData<Data> {
    public static final int NULL_STAMP = -1;

    private final @Nullable Data data;
    private final int stamp;
    private final boolean hasData;

    private GistData(@Nullable Data data,
                     int stamp,
                     boolean hasData) {
      this.data = data;
      this.stamp = stamp;
      this.hasData = hasData;
    }

    public @Nullable Data data() {
      return data;
    }

    public int gistStamp() {
      return stamp;
    }

    public boolean hasData() {
      return hasData;
    }

    /**
     * @return true if gist has no data because stored data is already outdated. Outdated gist
     * has no data, but has gistStamp
     */
    public boolean isOutdated() {
      return !hasData && stamp != NULL_STAMP;
    }

    public @Nullable Data dataIfExists() throws NoSuchElementException {
      if (!hasData) {
        throw new NoSuchElementException("Data doesn't exist");
      }
      return data;
    }

    public @Nullable Data dataOr(@Nullable Data defaultValueIfNotExists) {
      if (!hasData) {
        return defaultValueIfNotExists;
      }
      return data;
    }

    @Override
    public String toString() {
      if (!hasData) {
        if (stamp == NULL_STAMP) {
          return "GistData[<none>]";
        }
        else {
          return "GistData[<outdated>, stamp: " + stamp + "]";
        }
      }
      else {
        return "GistData[data: " + data + ", stamp: " + stamp + ']';
      }
    }


    static <Data> GistData<Data> empty() {
      return new GistData<>(null, NULL_STAMP, false);
    }

    static <Data> GistData<Data> outdated(int gistStamp) {
      if (gistStamp == NULL_STAMP) {
        throw new IllegalArgumentException("gistStamp(=" + gistStamp + ") must be valid (!=" + NULL_STAMP + ")");
      }
      return new GistData<>(null, gistStamp, false);
    }

    static <Data> GistData<Data> valid(@Nullable Data gistData,
                                       int gistStamp) {
      if (gistStamp == NULL_STAMP) {
        throw new IllegalArgumentException("gistStamp(=" + gistStamp + ") must be valid (!=" + NULL_STAMP + ")");
      }
      return new GistData<>(gistData, gistStamp, true);
    }
  }
}
