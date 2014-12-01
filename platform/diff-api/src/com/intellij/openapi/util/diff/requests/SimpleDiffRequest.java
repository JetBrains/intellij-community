package com.intellij.openapi.util.diff.requests;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.diff.contents.DiffContent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SimpleDiffRequest extends DiffRequestBase implements ContentDiffRequest {
  @NotNull private final DiffContent[] myContents;
  @NotNull private final String[] myContentTitles;
  @NotNull private final String myWindowTitle;

  public SimpleDiffRequest(@NotNull String windowTitle,
                           @NotNull DiffContent content1,
                           @NotNull DiffContent content2,
                           @NotNull String title1,
                           @NotNull String title2) {
    myWindowTitle = windowTitle;
    myContents = new DiffContent[]{content1, content2};
    myContentTitles = new String[]{title1, title2};
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

  @NotNull
  @Override
  public String getWindowTitle() {
    return myWindowTitle;
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    for (DiffContent content : myContents) {
      content.onAssigned(isAssigned);
    }
  }
}
