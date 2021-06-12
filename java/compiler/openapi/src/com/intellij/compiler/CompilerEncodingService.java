// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;

public abstract class CompilerEncodingService {
  public static CompilerEncodingService getInstance(@NotNull Project project) {
    return project.getService(CompilerEncodingService.class);
  }

  @Nullable
  public static Charset getPreferredModuleEncoding(Chunk<? extends Module> chunk) {
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

  @Nullable
  public abstract Charset getPreferredModuleEncoding(@NotNull Module module);

  @NotNull
  public abstract Collection<Charset> getAllModuleEncodings(@NotNull Module module);
}
