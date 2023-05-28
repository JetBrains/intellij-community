// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Service allows attaching named blobs ('gists') to a {@link VirtualFile}.
 * <p>
 * It is a direct analog of VFS file attributes, but VFS attributes are considered very low-level,
 * and have some implementation limitations (e.g. max size) -- this service is designed to be used
 * from upper levels, and to have no such limitations.
 * <p>
 * It is initially created for storing {@link VirtualFileGist}, hence some API peculiarities, and
 * hence it is marked as @Internal.
 * If you're about to use {@link GistStorage} service to cache something -- please, consider
 * {@link VirtualFileGist} first: likely {@link VirtualFileGist} fits better.
 *
 * @see VirtualFileGist
 */
@ApiStatus.Internal
public abstract class GistStorage {
  @NotNull
  public static GistStorage getInstance() {
    return ApplicationManager.getApplication().getService(GistStorage.class);
  }

  /**
   * Create a new {@link Gist}.
   *
   * @param id           a unique identifier of this gist
   * @param version      should be incremented each time the {@code externalizer} logic changes.
   * @param externalizer used to store the data to the disk and retrieve it
   * @param <Data>       the type of the data to cache
   * @return the gist object, where {@link Gist#getGlobalData} can later be used to retrieve the cached data
   */
  @NotNull
  public abstract <Data> Gist<Data> newGist(@NotNull @NonNls String id,
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

    static <Data> GistData<Data> noData() {
      return new GistData<>(null, NULL_STAMP, false);
    }

    static <Data> GistData<Data> outdated(int gistStamp) {
      return new GistData<>(null, gistStamp, false);
    }

    static <Data> GistData<Data> withData(@Nullable Data gistData,
                                          int gistStamp) {
      return new GistData<>(gistData, gistStamp, true);
    }
  }
}
