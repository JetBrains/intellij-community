// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;

@ApiStatus.Internal
public interface GenericZipEntry {
  long getSize();

  String getName();

  long getCrc();

  boolean isDirectory();

  InputStream getInputStream() throws IOException;
}
