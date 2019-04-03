// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.merge;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public class MergeCallback {
  public static final Key<MergeCallback> KEY = Key.create("com.intellij.diff.merge.MergeCallback");
  public static final MergeCallback EMPTY = new MergeCallback();

  public void applyResult(@NotNull MergeResult result) {
  }

  public boolean checkIsValid() {
    return true;
  }

  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
  }

  public boolean shouldRestoreOriginalContentOnCancel() {
    if (checkIsValid()) return true;
    return Messages.showYesNoDialog("Merge conflict is outdated. Restore file content prior to conflict resolve start?",
                                    DiffBundle.message("cancel.visual.merge.dialog.title"), "Restore", "Do nothing",
                                    Messages.getQuestionIcon()) == Messages.YES;
  }


  @NotNull
  public static <T extends MergeRequest> T register(@NotNull T request, @Nullable MergeCallback callback) {
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


  public interface Listener extends EventListener {
    default void fireConflictInvalid() {
    }
  }
}
