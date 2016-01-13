/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.contents;

import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.Nullable;

/**
 * Represents some data that probably can be compared with some other.
 *
 * @see DiffRequest
 */
public interface DiffContent extends UserDataHolder {
  @Nullable
  FileType getContentType();

  /**
   * Provides a way to open related content in editor
   */
  @Nullable
  OpenFileDescriptor getOpenFileDescriptor();

  /**
   * @see DiffRequest#onAssigned(boolean)
   */
  @CalledInAwt
  void onAssigned(boolean isAssigned);
}
