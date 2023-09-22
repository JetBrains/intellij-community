// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.messages;

import java.util.Collection;
import java.util.Collections;

public final class FileDeletedEvent extends BuildMessage {
  private final Collection<String> myFilePaths;

  public FileDeletedEvent(Collection<String> filePaths) {
    super("", Kind.INFO);
    myFilePaths = Collections.unmodifiableCollection(filePaths);
  }

  public Collection<String> getFilePaths() {
    return myFilePaths;
  }
}
