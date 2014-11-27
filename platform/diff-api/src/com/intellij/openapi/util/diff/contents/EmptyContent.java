package com.intellij.openapi.util.diff.contents;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;

/**
 * Represents empty content
 * <p/>
 * ex: 'Before' state for new file
 */
public class EmptyContent implements DiffContent {
  @Nullable
  @Override
  public FileType getContentType() {
    return null;
  }

  @Nullable
  @Override
  public OpenFileDescriptor getOpenFileDescriptor() {
    return null;
  }

  @Override
  public void onAssigned(boolean isAssigned) {
  }
}
