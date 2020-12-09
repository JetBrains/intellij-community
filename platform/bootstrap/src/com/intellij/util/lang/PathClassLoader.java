// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.lang;

import com.intellij.ide.BytecodeTransformer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.ProtectionDomain;

@ApiStatus.Internal
public final class PathClassLoader extends UrlClassLoader {
  private static final ClassPath.ResourceFileFactory RESOURCE_FILE_FACTORY =
    Boolean.getBoolean("idea.use.lock.free.zip.impl") ? file -> new ZipResourceFile(file) : null;

  private static final boolean isParallelCapable = USE_PARALLEL_LOADING && registerAsParallelCapable();

  private final BytecodeTransformer transformer;

  public PathClassLoader(@NotNull UrlClassLoader.Builder builder) {
    super(builder, RESOURCE_FILE_FACTORY, isParallelCapable);

    transformer = null;
  }

  public static ClassPath.ResourceFileFactory getResourceFileFactory() {
    return RESOURCE_FILE_FACTORY;
  }

  public PathClassLoader(Builder builder, BytecodeTransformer transformer) {
    super(builder, isParallelCapable);

    this.transformer = transformer;
  }

  @Override
  protected Class<?> _defineClass(String name, Resource resource, @Nullable ProtectionDomain protectionDomain) throws IOException {
    if (transformer == null || !transformer.isApplicable(name)) {
      if (RESOURCE_FILE_FACTORY != null) {
        ByteBuffer buffer = resource.getByteBuffer();
        if (buffer != null) {
          try {
            return defineClass(name, buffer, protectionDomain);
          }
          finally {
            resource.releaseByteBuffer(buffer);
          }
        }
      }

      byte[] data = resource.getBytes();
      return defineClass(name, data, 0, data.length, protectionDomain);
    }
    else {
      byte[] b = resource.getBytes();
      byte[] result = transformer.transform(this, name, protectionDomain, b);
      if (result != null) {
        b = result;
      }
      byte[] data = b;
      return defineClass(name, data, 0, data.length, protectionDomain);
    }
  }
}
