// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.*;

public final class IgnoredPatternSet {
  private final Set<String> masks;
  private final FileTypeAssocTable<Boolean> ignorePatterns = new FileTypeAssocTable<>();

  public IgnoredPatternSet() {
    masks = new LinkedHashSet<>();
  }

  IgnoredPatternSet(@NotNull List<String> masks) {
    FileNameMatcherFactory fileNameMatcherFactory = null;
    this.masks = new LinkedHashSet<>(masks.size());
    for (String ignoredFile : masks) {
      if (ignorePatterns.findAssociatedFileType(ignoredFile) == null) {
        this.masks.add(ignoredFile);
        if (fileNameMatcherFactory == null) {
          fileNameMatcherFactory = FileNameMatcherFactory.getInstance();
        }
        ignorePatterns.addAssociation(fileNameMatcherFactory.createMatcher(ignoredFile), Boolean.TRUE);
      }
    }
  }

  @NotNull Set<String> getIgnoreMasks() {
    return Collections.unmodifiableSet(masks);
  }

  public void setIgnoreMasks(@NotNull String list) {
    clearPatterns();

    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      addIgnoreMask(tokenizer.nextToken());
    }
  }

  void addIgnoreMask(@NotNull String ignoredFile) {
    if (ignorePatterns.findAssociatedFileType(ignoredFile) == null) {
      masks.add(ignoredFile);
      ignorePatterns.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(ignoredFile), Boolean.TRUE);
    }
  }

  public boolean isIgnored(@NotNull CharSequence fileName) {
    if (ignorePatterns.findAssociatedFileType(fileName) == Boolean.TRUE) {
      return true;
    }

    //Quite a hack, but still we need to have some name, which
    //won't be caught by VFS for sure.
    return StringUtilRt.endsWith(fileName, FileUtil.ASYNC_DELETE_EXTENSION);
  }

  void clearPatterns() {
    masks.clear();
    ignorePatterns.removeAllAssociations(Boolean.TRUE);
  }
}
