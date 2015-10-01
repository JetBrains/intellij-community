/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.requests;

import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.merge.TextMergeRequest;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TextMergeRequestImpl extends TextMergeRequest {
  @Nullable private final Project myProject;
  @NotNull private final DocumentContent myOutput;
  @NotNull private final List<DocumentContent> myContents;

  @NotNull private final CharSequence myOriginalContent;

  @Nullable private final String myTitle;
  @NotNull private final List<String> myTitles;

  @Nullable private final Consumer<MergeResult> myApplyCallback;

  public TextMergeRequestImpl(@Nullable Project project,
                              @NotNull DocumentContent output,
                              @NotNull CharSequence originalContent,
                              @NotNull List<DocumentContent> contents,
                              @Nullable String title,
                              @NotNull List<String> contentTitles,
                              @Nullable Consumer<MergeResult> applyCallback) {
    assert contents.size() == 3;
    assert contentTitles.size() == 3;
    myProject = project;

    myOutput = output;
    myOriginalContent = originalContent;

    myContents = contents;
    myTitles = contentTitles;
    myTitle = title;

    myApplyCallback = applyCallback;
  }

  @NotNull
  @Override
  public DocumentContent getOutputContent() {
    return myOutput;
  }

  @NotNull
  @Override
  public List<DocumentContent> getContents() {
    return myContents;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  @Override
  public List<String> getContentTitles() {
    return myTitles;
  }

  @Override
  public void applyResult(@NotNull MergeResult result) {
    final CharSequence applyContent;
    switch (result) {
      case CANCEL:
        applyContent = myOriginalContent;
        break;
      case LEFT:
        CharSequence leftContent = ThreeSide.LEFT.select(getContents()).getDocument().getImmutableCharSequence();
        applyContent = StringUtil.convertLineSeparators(leftContent.toString());
        break;
      case RIGHT:
        CharSequence rightContent = ThreeSide.RIGHT.select(getContents()).getDocument().getImmutableCharSequence();
        applyContent = StringUtil.convertLineSeparators(rightContent.toString());
        break;
      case RESOLVED:
        applyContent = null;
        break;
      default:
        throw new IllegalArgumentException(result.toString());
    }

    if (applyContent != null) {
      DiffUtil.executeWriteCommand(myOutput.getDocument(), myProject, null, new Runnable() {
        @Override
        public void run() {
          myOutput.getDocument().setText(applyContent);
        }
      });
    }

    if (myApplyCallback != null) myApplyCallback.consume(result);
  }
}
