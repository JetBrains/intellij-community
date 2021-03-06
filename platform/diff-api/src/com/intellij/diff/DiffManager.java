// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff;

import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Use {@link #showDiff}/{@link #showMerge} methods to show diff viewer in a frame or modal window.
 * <p>
 * Use {@link #createRequestPanel} to embed diff viewer as JComponent.
 * For a more fine-grained control over content behavior, extend {@link com.intellij.diff.impl.DiffRequestProcessor} directly.
 * <p>
 * Use {@link com.intellij.diff.util.DiffUserDataKeys} and {@link DiffRequest#putUserData}/{@link com.intellij.diff.contents.DiffContent#putUserData}
 * to pass additional hints (such as default scrollbar position). Note, that these hints might be ignored.
 * <p>
 * Use {@link DiffExtension} to customize existing diff viewers.
 * Register custom {@link FrameDiffTool} to support custom {@link DiffRequest} or to add new views for default ones.
 * <p>
 * <pre>
 * DiffContent content1 = DiffContentFactory.getInstance().create(project, "Stuff");
 * DiffContent content2 = DiffContentFactory.getInstance().createClipboardContent(project);
 * content2.putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
 * SimpleDiffRequest request = new SimpleDiffRequest("Stuff vs Clipboard", content1, content2, "Stuff", "Clipboard");
 * DiffManager.getInstance().showDiff(project, request);
 * </pre>
 *
 * @see DiffRequestFactory
 * @see DiffContentFactory
 * @see DiffRequestChain
 * @see com.intellij.diff.impl.CacheDiffRequestProcessor
 */
public abstract class DiffManager {
  @NotNull
  public static DiffManager getInstance() {
    return ApplicationManager.getApplication().getService(DiffManager.class);
  }

  /**
   * @see com.intellij.diff.requests.SimpleDiffRequest
   */
  @RequiresEdt
  public abstract void showDiff(@Nullable Project project, @NotNull DiffRequest request);

  @RequiresEdt
  public abstract void showDiff(@Nullable Project project, @NotNull DiffRequest request, @NotNull DiffDialogHints hints);

  /**
   * @see com.intellij.diff.chains.SimpleDiffRequestChain
   */
  @RequiresEdt
  public abstract void showDiff(@Nullable Project project, @NotNull DiffRequestChain requests, @NotNull DiffDialogHints hints);

  /**
   * Creates simple JComponent, capable of displaying {@link DiffRequest}.
   */
  @NotNull
  public abstract DiffRequestPanel createRequestPanel(@Nullable Project project, @NotNull Disposable parent, @Nullable Window window);

  @RequiresEdt
  public abstract void showMerge(@Nullable Project project, @NotNull MergeRequest request);
}
