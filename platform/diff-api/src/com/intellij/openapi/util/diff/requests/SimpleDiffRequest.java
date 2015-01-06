package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.util.diff.contents.DiffContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleDiffRequest extends DiffRequestBase implements ContentDiffRequest {
  @Nullable private final String myTitle;
  @NotNull private final DiffContent[] myContents;
  @NotNull private final String[] myContentTitles;

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @NotNull String title1,
                           @NotNull String title2) {
    this(title, new DiffContent[]{content1, content2}, new String[]{title1, title2});
  }

  public SimpleDiffRequest(@Nullable String title,
                           @NotNull DiffContent[] contents,
                           @NotNull String[] titles) {
    assert contents.length == titles.length;

    myTitle = title;
    myContents = contents;
    myContentTitles = titles;
  }

  @NotNull
  @Override
  public DiffContent[] getContents() {
    return myContents;
  }

  @NotNull
  @Override
  public String[] getContentTitles() {
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
