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
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jun 6, 2002
 * Time: 5:41:42 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;

public class CachedValueImpl<T> extends CachedValueBase<T> implements CachedValue<T> {
  private final CachedValueProvider<T> myProvider;

  public CachedValueImpl(@NotNull CachedValueProvider<T> provider) {
    myProvider = provider;
  }

  @Override
  protected <P> CachedValueProvider.Result<T> doCompute(P param) {
    return myProvider.compute();
  }

  @NotNull
  @Override
  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }

  @Override
  public T getValue() {
    return getValueWithLock(null);
  }

  @Override
  public boolean isFromMyProject(Project project) {
    return true;
  }
}
