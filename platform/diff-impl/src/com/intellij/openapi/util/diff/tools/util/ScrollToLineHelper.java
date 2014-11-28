package com.intellij.openapi.util.diff.tools.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.diff.api.DiffTool;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.util.diff.util.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScrollToLineHelper {
  protected boolean myShouldScroll = true;

  @Nullable protected ScrollToPolicy myScrollToChange;
  @Nullable protected Pair<Side, Integer> myScrollToLine;

  public void processContext(@NotNull DiffTool.DiffContext context, @NotNull DiffRequest request) {
    myScrollToChange = context.getUserData(DiffUserDataKeys.SCROLL_TO_CHANGE);
    myScrollToLine = request.getUserData(DiffUserDataKeys.SCROLL_TO_LINE);
  }

  public void onSuccessfulScroll() {
    myShouldScroll = false;

    myScrollToChange = null;
    myScrollToLine = null;
  }
}
