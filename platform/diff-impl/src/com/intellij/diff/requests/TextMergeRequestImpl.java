// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.requests;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.MergeCallback;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.MergeUtil;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public class TextMergeRequestImpl extends TextMergeRequest {
  private final @Nullable Project myProject;
  private final @NotNull DocumentContent myOutput;
  private final @NotNull List<DocumentContent> myContents;

  private final @NotNull CharSequence myOriginalContent;

  private final @Nullable @NlsContexts.DialogTitle String myTitle;
  private final @NotNull List<String> myTitles;

  public TextMergeRequestImpl(@Nullable Project project,
                              @NotNull DocumentContent output,
                              @NotNull CharSequence originalContent,
                              @NotNull List<DocumentContent> contents,
                              @Nullable @NlsContexts.DialogTitle String title,
                              @NotNull List<@Nls String> contentTitles) {
    assert contents.size() == 3;
    assert contentTitles.size() == 3;
    myProject = project;

    myOutput = output;
    myOriginalContent = originalContent;

    myContents = contents;
    myTitles = contentTitles;
    myTitle = title;
  }

  @Override
  public @NotNull DocumentContent getOutputContent() {
    return myOutput;
  }

  @Override
  public @NotNull List<DocumentContent> getContents() {
    return myContents;
  }

  @Override
  public @Nullable String getTitle() {
    return myTitle;
  }

  @Override
  public @NotNull List<String> getContentTitles() {
    return myTitles;
  }

  @Override
  public void applyResult(@NotNull MergeResult result) {
    final CharSequence applyContent;
    switch (result) {
      case CANCEL -> applyContent = MergeUtil.shouldRestoreOriginalContentOnCancel(this) ? myOriginalContent : null;
      case LEFT -> {
        CharSequence leftContent = ThreeSide.LEFT.select(getContents()).getDocument().getImmutableCharSequence();
        applyContent = StringUtil.convertLineSeparators(leftContent.toString());
      }
      case RIGHT -> {
        CharSequence rightContent = ThreeSide.RIGHT.select(getContents()).getDocument().getImmutableCharSequence();
        applyContent = StringUtil.convertLineSeparators(rightContent.toString());
      }
      case RESOLVED -> applyContent = null;
      default -> throw new IllegalArgumentException(result.toString());
    }

    if (applyContent != null) {
      DiffUtil.executeWriteCommand(myOutput.getDocument(), myProject, null, () -> myOutput.getDocument().setText(applyContent));
    }

    MergeCallback.getCallback(this).applyResult(result);
  }

  @Override
  public void onAssigned(boolean assigned) {
    myOutput.onAssigned(assigned);
    for (DocumentContent content : myContents) {
      content.onAssigned(assigned);
    }
  }
}
