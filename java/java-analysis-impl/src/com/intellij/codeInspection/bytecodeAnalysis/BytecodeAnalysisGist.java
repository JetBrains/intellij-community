// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.VirtualFileGist;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Holds the {@link VirtualFileGist} for bytecode analysis.
 * <p>
 * This is a separate service to avoid accessing {@link GistManager} during static class initialization of {@link ClassDataIndexer},
 * which violates platform rules (class initialization must not depend on services).
 * See LSP-390 to learn more.
 */
@Service
public final class BytecodeAnalysisGist {
  private final VirtualFileGist<Map<HMember, Equations>> gist;

  public BytecodeAnalysisGist() {
    gist = GistManager.getInstance().newVirtualFileGist(
      "BytecodeAnalysisIndex",
      ClassDataIndexer.FINAL_VERSION,
      BytecodeAnalysisIndex.EquationsExternalizer.INSTANCE,
      new ClassDataIndexer()
    );
  }

  public static @NotNull BytecodeAnalysisGist getInstance() {
    return ApplicationManager.getApplication().getService(BytecodeAnalysisGist.class);
  }

  public @NotNull VirtualFileGist<Map<HMember, Equations>> getGist() {
    return gist;
  }
}
