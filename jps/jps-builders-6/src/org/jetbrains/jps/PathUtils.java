/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.jps;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

public class PathUtils {

  @Nullable
  public static File convertToFile(final URI uri) {
    if (uri == null) {
      return null;
    }
    final String path = uri.getPath();
    if (path == null) {
      return null;
    }
    return new File(toURI(path));
  }

  public static URI toURI(String localPath) {
    try {
      final String p = FileUtilRt.toSystemIndependentName(localPath);
      final StringBuilder buf = new StringBuilder(p.length() + 3);
      if (!p.startsWith("/")) {
        buf.append("///");
      }
      else if (!p.startsWith("//")) {
        buf.append("//");
      }
      buf.append(p);
      return new URI("file", null, buf.toString(), null);
    }
    catch (URISyntaxException e) {
      throw new Error(e);
    }
  }
}
