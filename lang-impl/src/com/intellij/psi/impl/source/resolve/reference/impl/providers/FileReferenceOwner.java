package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public interface FileReferenceOwner {

  @Nullable
  FileReference getLastFileReference();
}
