// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Experimental
public interface CodeStyleSettingsModifier {
  @NotNull
  Dependencies modifySettings(@NotNull CodeStyleSettings baseSettings, @NotNull PsiFile file);

  interface Dependencies {
    Collection<Object> getAll();
    boolean isEmpty();
  }

  @SuppressWarnings("unused")
  class SingleDependency implements Dependencies {
    private final Object myDependency;

    public SingleDependency(@NotNull Object dependency) {
      myDependency = dependency;
    }

    @Override
    public Collection<Object> getAll() {
      return Collections.singletonList(myDependency);
    }

    @Override
    public boolean isEmpty() {
      return false;
    }
  }

  @SuppressWarnings("unused")
  class DependencyList implements Dependencies {
    private final List<Object> myDependencies = new ArrayList<>();

    public void add(@NotNull Object dependency) {
      myDependencies.add(dependency);
    }

    public void add(@NotNull Dependencies dependencies) {
      myDependencies.add(dependencies.getAll());
    }

    @Override
    public Collection<Object> getAll() {
      return myDependencies;
    }

    @Override
    public boolean isEmpty() {
      return myDependencies.isEmpty();
    }
  }
}
