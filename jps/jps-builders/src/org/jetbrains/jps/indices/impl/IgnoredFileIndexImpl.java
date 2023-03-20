// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.indices.impl;

import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.model.JpsModel;

public final class IgnoredFileIndexImpl implements IgnoredFileIndex {
  private final IgnoredPatternSet myIgnoredPatterns = new IgnoredPatternSet();

  public IgnoredFileIndexImpl(JpsModel model) {
    myIgnoredPatterns.setIgnoreMasks(model.getGlobal().getFileTypesConfiguration().getIgnoredPatternString());
  }

  @Override
  public boolean isIgnored(String fileName) {
    return myIgnoredPatterns.isIgnored(fileName);
  }
}
