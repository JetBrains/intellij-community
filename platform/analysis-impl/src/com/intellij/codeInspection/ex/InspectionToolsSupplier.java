// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class InspectionToolsSupplier {
  protected final Collection<Listener> myListeners = new SmartList<>();

  @NotNull
  public abstract List<InspectionToolWrapper> createTools();

  public void addListener(@NotNull Listener listener, @Nullable Disposable parentDisposable) {
    myListeners.add(listener);
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, () -> myListeners.remove(listener));
    }
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