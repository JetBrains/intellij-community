// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.module;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

public interface JpsDependenciesRootsEnumerator {
  Collection<String> getUrls();

  Collection<File> getRoots();

  Collection<Path> getPaths();
}
