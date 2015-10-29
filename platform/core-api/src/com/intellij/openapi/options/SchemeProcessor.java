/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jdom.Parent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SchemeProcessor<T extends ExternalizableScheme> {
  public abstract Parent writeScheme(@NotNull T scheme) throws WriteExternalException;

  public void initScheme(@NotNull T scheme) {
  }

  public void onSchemeAdded(@NotNull T scheme) {
  }

  public void onSchemeDeleted(@NotNull T scheme) {
  }

  /**
   * Scheme switched.
   */
  public void onCurrentSchemeChanged(@Nullable Scheme oldScheme) {
  }

  @Nullable
  protected T readScheme(@NotNull Element element) throws Exception {
    throw new AbstractMethodError();
  }

  @Nullable
  /**
   * @param duringLoad If occurred during {@link SchemesManager#loadSchemes()} call
   * Returns null if element is not valid.
   */
  public T readScheme(@NotNull Element element, boolean duringLoad) throws Exception {
    return readScheme(element);
  }

  public enum State {
    UNCHANGED, NON_PERSISTENT, POSSIBLY_CHANGED
  }

  @NotNull
  public State getState(@NotNull T scheme) {
    return State.POSSIBLY_CHANGED;
  }
}
