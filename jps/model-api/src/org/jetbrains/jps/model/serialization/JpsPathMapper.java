// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.model.serialization;

public interface JpsPathMapper {
  String mapUrl(String url);

  JpsPathMapper IDENTITY = new JpsPathMapper() {
    @Override
    public String mapUrl(String url) {
      return url;
    }
  };
}
