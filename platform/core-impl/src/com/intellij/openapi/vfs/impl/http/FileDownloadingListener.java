/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.http;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface FileDownloadingListener {

  void fileDownloaded(@NotNull VirtualFile localFile);

  void errorOccurred(@NlsContexts.DialogMessage @NotNull String errorMessage);

  void downloadingStarted();

  void downloadingCancelled();

  void progressMessageChanged(final boolean indeterminate, @NlsContexts.ProgressText @NotNull String message);

  void progressFractionChanged(double fraction);
}
