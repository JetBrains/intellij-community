// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.library.impl;

import com.intellij.framework.library.DownloadableLibraryFileDescription;
import com.intellij.util.download.impl.DownloadableFileDescriptionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DownloadableLibraryFileDescriptionImpl extends DownloadableFileDescriptionImpl implements DownloadableLibraryFileDescription {
  private final DownloadableFileDescriptionImpl mySourceDownloadUrl;
  private final DownloadableFileDescriptionImpl myDocumentationDownloadUrl;
  private final boolean myOptional;

  public DownloadableLibraryFileDescriptionImpl(@NotNull String downloadUrl,
                                                @NotNull String fileName,
                                                @NotNull String fileExtension,
                                                @Nullable String sourceDownloadUrl,
                                                @Nullable String documentationDownloadUrl,
                                                boolean optional) {
    super(downloadUrl, fileName, fileExtension);
    mySourceDownloadUrl = sourceDownloadUrl != null ? new DownloadableFileDescriptionImpl(sourceDownloadUrl, fileName +"-sources", fileExtension) : null;
    myDocumentationDownloadUrl = documentationDownloadUrl != null ? new DownloadableFileDescriptionImpl(documentationDownloadUrl, fileName+"-javadoc", fileExtension) : null;
    myOptional = optional;
  }

  @Override
  public DownloadableFileDescriptionImpl getSourcesDescription() {
    return mySourceDownloadUrl;
  }

  @Override
  public DownloadableFileDescriptionImpl getDocumentationDescription() {
    return myDocumentationDownloadUrl;
  }

  @Override
  public boolean isOptional() {
    return myOptional;
  }
}
