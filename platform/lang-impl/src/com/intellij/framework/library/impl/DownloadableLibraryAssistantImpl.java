/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.framework.library.impl;

import com.intellij.framework.library.DownloadableFileDescription;
import com.intellij.framework.library.DownloadableLibraryAssistant;
import com.intellij.framework.library.DownloadableLibraryDescription;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author nik
 */
public class DownloadableLibraryAssistantImpl extends DownloadableLibraryAssistant {
  @NotNull
  @Override
  public DownloadableFileDescription createFileDescription(@NotNull String downloadUrl, @NotNull String fileName) {
    return new DownloadableFileDescriptionImpl(downloadUrl, FileUtil.getNameWithoutExtension(fileName), FileUtil.getExtension(fileName));
  }

  @NotNull
  @Override
  public DownloadableLibraryDescription createLibraryDescription(@NotNull String groupId, @NotNull URL... localUrls) {
    return new LibraryVersionsFetcher(groupId, localUrls);
  }
}
