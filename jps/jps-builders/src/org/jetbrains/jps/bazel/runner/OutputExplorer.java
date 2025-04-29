// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.runner;

import org.jetbrains.annotations.Nullable;

public interface OutputExplorer {

  Iterable<String> listFiles(String packageName, boolean recurse);

  byte @Nullable [] getFileContent(String path);
}
