// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.merge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

public class MergeCallback {
  private static final Logger LOG = getInstance(MergeCallback.class);

  private static final Key<MergeCallback> KEY = Key.create("com.intellij.diff.merge.MergeCallback");
  private static final MergeCallback EMPTY = new MergeCallback();

  public void applyResult(@NotNull MergeResult result) {
  }

  public boolean checkIsValid() {
    return true;
  }

  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
  }


  public static @NotNull <T extends MergeRequest> T register(@NotNull T request, @Nullable MergeCallback callback) {
    LOG.assertTrue(request.getUserData(KEY) == null);
    if (callback != null) request.putUserData(KEY, callback);
    return request;
  }

  public static @NotNull <T extends MergeRequest> T register(@NotNull T request, @Nullable Consumer<? super MergeResult> callback) {
    MergeCallback mergeCallback = callback == null ? null : new MergeCallback() {
      @Override
      public void applyResult(@NotNull MergeResult result) {
        callback.consume(result);
      }
    };
    return register(request, mergeCallback);
  }

  public static @NotNull MergeCallback getCallback(@NotNull MergeRequest request) {
    MergeCallback callback = request.getUserData(KEY);
    return callback != null ? callback : EMPTY;
  }

  /**
   * Transfer MergeCallback from original to target.
   * <p>
   * After this call lifecycle of the original request is finished, and it can be discarded.
   * Target request shall be handled like usual, via {@link MergeRequest#applyResult(MergeResult)} call.
   */
  @SuppressWarnings("unused") // Used in Rider
  public static void retarget(@NotNull MergeRequest original, @NotNull MergeRequest target) {
    MergeCallback callback = original.getUserData(KEY);
    original.putUserData(KEY, null);
    register(target, callback);
  }

  public interface Listener extends EventListener {
    default void fireConflictInvalid() {
    }
  }
}
