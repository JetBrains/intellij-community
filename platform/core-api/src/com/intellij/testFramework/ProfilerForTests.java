// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import java.io.IOException;
import java.nio.file.Path;

public interface ProfilerForTests {
  void startProfiling(Path logDir, String fileName) throws IOException;

  void stopProfiling() throws IOException;
}
