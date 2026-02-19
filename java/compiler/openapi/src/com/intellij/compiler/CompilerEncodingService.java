// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.Charset;
import java.util.Collection;

public abstract class CompilerEncodingService {
  public static CompilerEncodingService getInstance(@NotNull Project project) {
    return project.getService(CompilerEncodingService.class);
  }

  public static @Nullable Charset getPreferredModuleEncoding(Chunk<? extends Module> chunk) {
    CompilerEncodingService service = null;
    for (Module module : chunk.getNodes()) {
      if (service == null) {
        service = getInstance(module.getProject());
      }
      final Charset charset = service.getPreferredModuleEncoding(module);
      if (charset != null) {
        return charset;
      }
    }
    return null;
  }

  public abstract @Nullable Charset getPreferredModuleEncoding(@NotNull Module module);

  public abstract @NotNull @Unmodifiable Collection<Charset> getAllModuleEncodings(@NotNull Module module);
}
