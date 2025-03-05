package com.intellij.database.datagrid;

import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GridRequestSource {
  public final RequestPlace place;
  private final ActionCallback myCallback = new ActionCallback();
  public Phase phase = Phase.FIRST;
  private boolean myErrorOccurred;
  private String myErrorMessage;
  private boolean myMutatedDataLocally;

  public GridRequestSource(@Nullable RequestPlace place) {
    this.place = place;
  }

  public @NotNull ActionCallback getActionCallback() {
    return myCallback;
  }

  public static GridRequestSource create(@Nullable RequestPlace source) {
    return new GridRequestSource(source);
  }

  public void requestComplete(boolean success) {
    if (success) {
      myCallback.setDone();
    }
    else {
      myCallback.setRejected();
    }
  }

  public void clearError() {
    myErrorOccurred = false;
    myErrorMessage = null;
  }

  public void setErrorOccurred(@NotNull String message) {
    myErrorOccurred = true;
    myErrorMessage = message;
  }

  public boolean errorOccurred() {
    return myErrorOccurred;
  }

  public @Nullable String getErrorMessage() {
    return myErrorMessage;
  }

  public void setMutatedDataLocally(boolean value) {
    myMutatedDataLocally = value;
  }

  public boolean isMutatedDataLocally() {
    return myMutatedDataLocally;
  }

  public enum Phase {
    PREPARE,
    FIRST,
    LOAD_VALUES_USING_KEYS,
    COUNT
  }

  public interface RequestPlace {
  }

  public interface GridRequestPlace<Row, Column> extends RequestPlace {
    @NotNull CoreGrid<Row, Column> getGrid();
  }
}
