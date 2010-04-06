/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class StubIndex {

  private static class StubIndexHolder {
    private static final StubIndex ourInstance = ApplicationManager.getApplication().getComponent(StubIndex.class);
  }

  public static StubIndex getInstance() {
    return StubIndexHolder.ourInstance;
  }

  public abstract <Key, Psi extends PsiElement> Collection<Psi> get(
      @NotNull StubIndexKey<Key, Psi> indexKey, @NotNull Key key, final Project project, final GlobalSearchScope scope
  );

  public abstract <Key> Collection<Key> getAllKeys(StubIndexKey<Key, ?> indexKey, @NotNull Project project);
}
