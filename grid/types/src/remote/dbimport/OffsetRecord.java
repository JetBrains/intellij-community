package com.intellij.database.remote.dbimport;

import org.jetbrains.annotations.NotNull;

public class OffsetRecord extends ErrorRecord.ExceptionRecord {
  private final long myStartOffset;

  public OffsetRecord(@NotNull Exception exception, long number, long startOffset) {
    super(exception, number);
    myStartOffset = startOffset;
  }

  @Override
  public @NotNull String getMessage() {
    return String.format("%s:%s: %s", getLineNumber() + 1, myStartOffset + 1, getErrorText());
  }

  @Override
  public int compareTo(@NotNull ErrorRecord o) {
    int result = super.compareTo(o);
    if (!(o instanceof OffsetRecord) || result != 0) return result;
    OffsetRecord casted = (OffsetRecord)o;
    return Long.compare(myStartOffset, casted.myStartOffset);
  }
}
