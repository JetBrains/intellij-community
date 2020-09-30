/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  @NotNull
  public KeyDescriptor<String> getKeyDescriptor() {
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