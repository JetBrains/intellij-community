// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JpsPathUtilTest {
  @Test void fileUrlToPath() {
    assertEquals("/path/to/some!/file", JpsPathUtil.urlToPath("file:///path/to/some!/file"));
  }

  @Test void jarUrlToPath() {
    assertEquals("/path/to!/some.jar", JpsPathUtil.urlToPath("jar:///path/to!/some.jar!/"));
    assertEquals("/path/to!/some.jar", JpsPathUtil.urlToPath("jar:///path/to!/some.jar!/inner"));
  }
}
