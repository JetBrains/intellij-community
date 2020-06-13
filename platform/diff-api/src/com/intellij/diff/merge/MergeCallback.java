// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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


  @NotNull
  public static <T extends MergeRequest> T register(@NotNull T request, @Nullable MergeCallback callback) {
    LOG.assertTrue(request.getUserData(KEY) == null);
    if (callback != null) request.putUserData(KEY, callback);
    return request;
  }

  @NotNull
  public static <T extends MergeRequest> T register(@NotNull T request, @Nullable Consumer<? super MergeResult> callback) {
    return register(request, new MergeCallback() {
      @Override
      public void applyResult(@NotNull MergeResult result) {
        if (callback != null) callback.consume(result);
      }
    });
  }

  @NotNull
  public static MergeCallback getCallback(@NotNull MergeRequest request) {
    MergeCallback callback = request.getUserData(KEY);
    return callback != null ? callback : EMPTY;
  }

  // original request lifecycle is ended, MergeCallback is transferred from original to target
  public static void retarget(@NotNull MergeRequest original, @NotNull MergeRequest target) {
    original.resultRetargeted();

    MergeCallback callback = original.getUserData(KEY);
    original.putUserData(KEY, null);
    target.putUserData(KEY, callback);
  }

  public interface Listener extends EventListener {
    default void fireConflictInvalid() {
    }
  }
}
