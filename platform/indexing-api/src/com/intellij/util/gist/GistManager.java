// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.gist;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A helper class for working with file gists: associating persistent data with current VFS or PSI file contents.
 * @author peter
 */
public abstract class GistManager {
  @NotNull
  public static GistManager getInstance() {
    return ApplicationManager.getApplication().getService(GistManager.class);
  }

  /**
   * Create a new {@link VirtualFileGist}.
   * @param id a unique identifier of this data
   * @param version should be incremented each time the {@code externalizer} or {@code calcData} logic changes.
   * @param externalizer used to store the data to the disk and retrieve it
   * @param calcData calculates the data by the file content when needed
   * @param <Data> the type of the data to cache
   * @return the gist object, where {@link VirtualFileGist#getFileData} can later be used to retrieve the cached data
   */
  @NotNull
  public abstract <Data> VirtualFileGist<Data> newVirtualFileGist(@NotNull @NonNls String id,
                                                                  int version,
                                                                  @NotNull DataExternalizer<Data> externalizer,
                                                                  @NotNull VirtualFileGist.GistCalculator<Data> calcData);

  /**
   * Create a new {@link PsiFileGist}.
   * @param <Data> the type of the data to cache
   * @param id a unique identifier of this data
   * @param version should be incremented each time the {@code externalizer} or {@code calcData} logic changes.
   * @param externalizer used to store the data to the disk and retrieve it
   * @param calcData calculates the data by the file content when needed
   * @return the gist object, where {@link PsiFileGist#getFileData} can later be used to retrieve the cached data
   */
  @NotNull
  public abstract <Data> PsiFileGist<Data> newPsiFileGist(@NotNull @NonNls String id,
                                                          int version,
                                                          @NotNull DataExternalizer<Data> externalizer,
                                                          @NotNull NullableFunction<? super PsiFile, ? extends Data> calcData);

  /**
   * Force all gists to be recalculated on the next request.
   */
  public abstract void invalidateData();

  /**
   * Force all gists for the given file to be recalculated on the next request.
   */
  public abstract void invalidateData(@NotNull VirtualFile file);

}
