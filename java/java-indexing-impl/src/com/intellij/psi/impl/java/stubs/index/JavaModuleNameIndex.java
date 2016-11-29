/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class JavaModuleNameIndex extends StringStubIndexExtension<PsiJavaModule> {
  private static final JavaModuleNameIndex ourInstance = new JavaModuleNameIndex();

  public static JavaModuleNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping ? 2 : 0);
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiJavaModule> getKey() {
    return JavaStubIndexKeys.MODULE_NAMES;
  }

  @Override
  public Collection<PsiJavaModule> get(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope(scope), PsiJavaModule.class);
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  }
}