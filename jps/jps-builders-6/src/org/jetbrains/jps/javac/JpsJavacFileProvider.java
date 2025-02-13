// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.util.Set;

@ApiStatus.Internal
public interface JpsJavacFileProvider {
  @Nullable
  Iterable<JavaFileObject> list(JavaFileManager.Location location,
                                String packageName,
                                Set<JavaFileObject.Kind> kinds,
                                boolean recurse);

  String inferBinaryName(JavaFileManager.Location location, JavaFileObject file);

  @Nullable JavaFileObject getFileForOutput(String fileName, String className, FileObject sibling);
}
