// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to provide {@link SourcePosition} for a {@link NodeDescriptor}.
 * <p>Used in Jump to Source action and inline debugger
 * @see DefaultSourcePositionProvider
 */
public abstract class SourcePositionProvider {
  public static final ExtensionPointName<SourcePositionProvider> EP_NAME = ExtensionPointName.create("com.intellij.debugger.sourcePositionProvider");

  @Nullable
  public static SourcePosition getSourcePosition(@NotNull NodeDescriptor descriptor,
                                                 @NotNull Project project,
                                                 @NotNull DebuggerContextImpl context
  ) {
    return getSourcePosition(descriptor, project, context, false);
  }

  @Nullable
  public static SourcePosition getSourcePosition(@NotNull NodeDescriptor descriptor,
                                                 @NotNull Project project,
                                                 @NotNull DebuggerContextImpl context,
                                                 boolean nearest
  ) {
    return DebuggerUtilsImpl.computeSafeIfAny(EP_NAME, provider -> {
      try {
        return provider.computeSourcePosition(descriptor, project, context, nearest);
      }
      catch (IndexNotReadyException e) {
        return null;
      }
    });
  }

  @Nullable
  protected abstract SourcePosition computeSourcePosition(@NotNull NodeDescriptor descriptor,
                                                          @NotNull Project project,
                                                          @NotNull DebuggerContextImpl context,
                                                          boolean nearest
  );
}
