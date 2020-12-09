// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.requests;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @see DiffContentFactory
 */
public class SimpleDiffRequest extends ContentDiffRequest {
  @Nullable private final @NlsContexts.DialogTitle String myTitle;
  @NotNull private final List<DiffContent> myContents;
  @NotNull private final List<String> myContentTitles;

  /**
   * Pass {@link DiffContentFactory#createEmpty()} to create request for additions/deletions.
   */
  public SimpleDiffRequest(@Nullable @NlsContexts.DialogTitle String title,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @Nullable @NlsContexts.Label String title1,
                           @Nullable @NlsContexts.Label String title2) {
    this(title, Arrays.asList(content1, content2), Arrays.asList(title1, title2));
  }

  public SimpleDiffRequest(@Nullable @NlsContexts.DialogTitle String title,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @NotNull DiffContent content3,
                           @Nullable @NlsContexts.Label String title1,
                           @Nullable @NlsContexts.Label String title2,
                           @Nullable @NlsContexts.Label String title3) {
    this(title, Arrays.asList(content1, content2, content3), Arrays.asList(title1, title2, title3));
  }

  public SimpleDiffRequest(@Nullable @NlsContexts.DialogTitle String title,
                           @NotNull List<DiffContent> contents,
                           @NotNull List<@NlsContexts.Label String> titles) {
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

  @NlsContexts.DialogTitle
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

  @Override
  public String toString() {
    return super.toString() + ":" + getContents();
  }
}
