// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.requests;

import com.intellij.diff.contents.DiffContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class SimpleDiffRequest extends ContentDiffRequest {
  @Nullable private final String myTitle;
  @NotNull private final List<DiffContent> myContents;
  @NotNull private final List<String> myContentTitles;

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @Nullable String title1,
                           @Nullable String title2) {
    this(title, Arrays.asList(content1, content2), Arrays.asList(title1, title2));
  }

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @NotNull DiffContent content3,
                           @Nullable String title1,
                           @Nullable String title2,
                           @Nullable String title3) {
    this(title, Arrays.asList(content1, content2, content3), Arrays.asList(title1, title2, title3));
  }

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull List<DiffContent> contents,
                           @NotNull List<String> titles) {
    assert contents.size() == titles.size();

    myTitle = title;
    myContents = contents;
    myContentTitles = titles;
  }

  @NotNull
  @Override
  public List<DiffContent> getContents() {
    return myContents;
  }

  @NotNull
  @Override
  public List<String> getContentTitles() {
    return myContentTitles;
  }

  @Nullable
  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    for (DiffContent content : myContents) {
      content.onAssigned(isAssigned);
    }
  }
}
