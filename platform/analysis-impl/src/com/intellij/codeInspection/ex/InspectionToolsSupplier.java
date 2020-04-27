// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.Disposable;
import com.intellij.util.containers.DisposableWrapperList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InspectionToolsSupplier implements Disposable {
  protected final DisposableWrapperList<Listener> myListeners = new DisposableWrapperList<>();

  @NotNull
  public abstract List<InspectionToolWrapper> createTools();

  public void addListener(@NotNull Listener listener, @Nullable Disposable parentDisposable) {
    if (parentDisposable != null) {
      myListeners.add(listener, parentDisposable);
    }
    else {
      myListeners.add(listener);
    }
  }

  @Override
  public void dispose() {
    myListeners.clear();
  }

  public interface Listener {
    void toolAdded(@NotNull InspectionToolWrapper inspectionTool);

    void toolRemoved(@NotNull InspectionToolWrapper inspectionTool);
  }

  public static class Simple extends InspectionToolsSupplier {
    @NotNull
    private final List<InspectionToolWrapper> myTools;

    public Simple(@NotNull List<InspectionToolWrapper> tools) {
      myTools = tools;
    }

    @NotNull
    @Override
    public List<InspectionToolWrapper> createTools() {
      return myTools;
    }
  }
}