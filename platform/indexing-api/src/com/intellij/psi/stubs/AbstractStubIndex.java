// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class AbstractStubIndex<Key, Psi extends PsiElement> implements StubIndexExtension<Key, Psi> {
  public Collection<Key> getAllKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(getKey(), project);
  }

  public boolean processAllKeys(Project project, Processor<? super Key> processor) {
    return StubIndex.getInstance().processAllKeys(getKey(), project, processor);
  }

  public Collection<Psi> get(@NotNull Key key, @NotNull final Project project, @NotNull final GlobalSearchScope scope) {
    return StubIndex.getInstance().get(getKey(), key, project, scope);
  }

  @Override
  public int getCacheSize() { return 2 * 1024; }
}
