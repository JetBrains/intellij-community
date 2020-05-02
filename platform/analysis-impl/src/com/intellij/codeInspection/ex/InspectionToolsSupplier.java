// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.Disposable;
import com.intellij.util.containers.DisposableWrapperList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InspectionToolsSupplier implements Disposable {
  protected final DisposableWrapperList<Listener> listeners = new DisposableWrapperList<>();

  public abstract @NotNull List<InspectionToolWrapper<?, ?>> createTools();

  public void addListener(@NotNull Listener listener, @Nullable Disposable parentDisposable) {
    if (parentDisposable == null) {
      listeners.add(listener);
    }
    else {
      listeners.add(listener, parentDisposable);
    }
  }

  @Override
  public void dispose() {
    listeners.clear();
  }

  public interface Listener {
    void toolAdded(@NotNull InspectionToolWrapper<?, ?> inspectionTool);

    void toolRemoved(@NotNull InspectionToolWrapper<?, ?> inspectionTool);
  }

  public static final class Simple extends InspectionToolsSupplier {
    private final @NotNull List<InspectionToolWrapper<?, ?>> myTools;

    public Simple(@NotNull List<InspectionToolWrapper<?, ?>> tools) {
      myTools = tools;
    }

    @Override
    public @NotNull List<InspectionToolWrapper<?, ?>> createTools() {
      return myTools;
    }
  }
}