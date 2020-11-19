// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

public class JpsWslPathMapper implements JpsPathMapper {
  private static final String WSL_PREFIX = "//wsl$/";

  @Override
  public String mapUrl(String url) {
    if (url.contains(WSL_PREFIX)) {
      int startPos = url.indexOf(WSL_PREFIX);
      int endPos = url.indexOf('/', startPos + WSL_PREFIX.length());
      if (endPos >= 0) {
        return url.substring(0, startPos) + url.substring(endPos);
      }
    }
    return url;
  }
}
