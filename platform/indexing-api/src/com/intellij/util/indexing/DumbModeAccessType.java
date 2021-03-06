// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The enum represents special index access types in dumb mode.
 * <p>
 * Usually index access in dumb mode is prohibited and the only possible result is {@link com.intellij.openapi.project.IndexNotReadyException}.
 * However in some case it might be useful to access incomplete or out-dated indexes while indexing in progress.
 * <p>
 * Index access in dumb mode can be done using any of {@link DumbModeAccessType}-s and
 * {@link DumbModeAccessType#ignoreDumbMode(ThrowableComputable)} or
 * {@link DumbModeAccessType#ignoreDumbMode(Runnable)}.
 * {@link DumbModeAccessType} controls which kind of data will be returned from index.
 */
@ApiStatus.Experimental
public enum DumbModeAccessType {
  /**
   * If index is accessed with {@link DumbModeAccessType#RELIABLE_DATA_ONLY} then only up-to-date
   * indexed data can be returned as a result of index query.
   */
  RELIABLE_DATA_ONLY,
  /**
   * When a client queries index with {@link DumbModeAccessType#RAW_INDEX_DATA_ACCEPTABLE},
   * any (even invalid) data currently present in index might be returned.
   * <p>
   * Note, this type doesn't work for {@link com.intellij.psi.stubs.StubIndex}.
   */
  RAW_INDEX_DATA_ACCEPTABLE;

  /**
   * Executes command with a requested access type and allows it to have index access in dumb mode.
   * Inside the command it's safe to call index related stuff and
   * {@link com.intellij.openapi.project.IndexNotReadyException} are not expected to be happen here.
   *
   * <p> In smart mode, the behavior is similar to direct command execution
   * @param command - a command to execute
   */
  public void ignoreDumbMode(@NotNull Runnable command) {
    FileBasedIndex.getInstance().ignoreDumbMode(this, command);
  }

  /**
   * Executes command with a requested access type and allows it to have index access in dumb mode.
   * Inside the command it's safe to call index related stuff and
   * {@link com.intellij.openapi.project.IndexNotReadyException} are not expected to be happen here.
   *
   * <p> In smart mode, the behavior is similar to direct command execution
   * @param computable - a command to execute
   */
  public <T, E extends Throwable> T ignoreDumbMode(@NotNull ThrowableComputable<T, E> computable) throws E {
    return FileBasedIndex.getInstance().ignoreDumbMode(this, computable);
  }
}
