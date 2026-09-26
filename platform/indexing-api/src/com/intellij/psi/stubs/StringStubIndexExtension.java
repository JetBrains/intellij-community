// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

public abstract class StringStubIndexExtension<Psi extends PsiElement> extends AbstractStubIndex<String, Psi> {
  @Override
  public int getVersion() {
    return 2;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  /**
   * If true then {@code <key hash> -> <virtual file id>} mapping will be saved in the persistent index structure.
   * It will then be used inside {@link StubIndex#processAllKeys(StubIndexKey, Processor, GlobalSearchScope, IdFilter)},
   * accepting {@link IdFilter} as a coarse filter to exclude keys from unrelated virtual files from further processing.
   * Otherwise, {@link IdFilter} parameter of this method will be ignored.
   * <p>
   * This property might come useful for optimizing "Go to Class/Symbol" and completion performance in case of multiple indexed projects.
   *
   * @see IdFilter#buildProjectIdFilter(Project, boolean)
   */
  public boolean traceKeyHashToVirtualFileMapping() {
    return false;
  }
}