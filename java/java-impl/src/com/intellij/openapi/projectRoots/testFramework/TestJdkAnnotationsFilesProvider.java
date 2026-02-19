// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.testFramework;

import java.nio.file.Path;


public interface TestJdkAnnotationsFilesProvider {
  Path getJdkAnnotationsPath();
}
