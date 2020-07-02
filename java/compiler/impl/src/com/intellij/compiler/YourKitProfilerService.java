// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import java.io.IOException;
import java.nio.file.Path;

public interface YourKitProfilerService {
  void copyYKLibraries(Path destination) throws IOException;
  String getYKAgentFullName();
}
