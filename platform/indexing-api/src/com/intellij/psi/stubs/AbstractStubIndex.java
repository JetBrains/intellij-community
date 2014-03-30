/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;

import java.util.Collection;

/**
 * @author max
 */
public abstract class AbstractStubIndex<Key, Psi extends PsiElement> implements StubIndexExtension<Key, Psi> {
  public Collection<Key> getAllKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(getKey(), project);
  }

  public boolean processAllKeys(Project project, Processor<Key> processor) {
    return StubIndex.getInstance().processAllKeys(getKey(), project, processor);
  }

  public Collection<Psi> get(Key key, final Project project, final GlobalSearchScope scope) {
    return StubIndex.getInstance().get(getKey(), key, project, scope);
  }

  public int getCacheSize() { return 2 * 1024; }
}
