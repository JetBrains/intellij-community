// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.file;

import com.intellij.largeFilesEditor.accessGettingPageTokens.AccessGettingPageToken;
import com.intellij.largeFilesEditor.editor.Page;
import com.intellij.largeFilesEditor.search.searchTask.FileDataProviderForSearch;
import org.jetbrains.annotations.CalledInAwt;

import java.io.IOException;
import java.nio.charset.Charset;

public interface FileManager {

  void reset(Charset charset);

  @CalledInAwt
  void needToOpenNewPage(AccessGettingPageToken token);

  void dispose();

  String getCharsetName();

  long getPagesAmount() throws IOException;

  int getPageSize();

  int getMaxBorderShift();

  Page getPage_wait(long pageNumber) throws IOException;

  void beginSavingFile();

  void cancelSaving();

  boolean canFileBeReloadedInOtherCharset();

  String getFileName();

  FileDataProviderForSearch getFileDataProviderForSearch();

  void requestReadPage(long pageNumber, ReadingPageResultHandler readingPageResultHandler);
}
