/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author max
 */
public class InvalidVirtualFileAccessException extends RuntimeException {
  public InvalidVirtualFileAccessException(@NotNull VirtualFile file) {
    super(composeMessage(file));
  }

  public InvalidVirtualFileAccessException(@NotNull String message) {
    super(message);
  }

  private static String composeMessage(@NotNull VirtualFile file) {
    String url = file.getUrl();
    String message = "Accessing invalid virtual file: " + url;

    try {
      VirtualFile found = VirtualFileManager.getInstance().findFileByUrl(url);
      message += "; original:" + hashCode(file) + "; found:" + hashCode(found);
      if (file.isInLocalFileSystem()) {
        boolean physicalExists = new File(file.getPath()).exists();
        message += "; File.exists()=" + physicalExists;
      }
      else {
        message += "; file system=" + file.getFileSystem();
      }
    }
    catch (Throwable t) {
      message += "; lookup failed: " + t.getMessage();
    }

    return message;
  }

  private static String hashCode(Object o) {
    return o == null ? "-" : String.valueOf(o.hashCode());
  }
}