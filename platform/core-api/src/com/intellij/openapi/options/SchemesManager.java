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

import com.intellij.openapi.util.Condition;
import com.intellij.util.ThrowableConvertor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public abstract class SchemesManager<T extends Scheme, E extends ExternalizableScheme> {
  @NotNull
  public abstract Collection<E> loadSchemes();

  public abstract void addNewScheme(@NotNull T scheme, boolean replaceExisting);

  public void addScheme(@NotNull T scheme) {
    addNewScheme(scheme, true);
  }

  /**
   * Consider to use {@link #setSchemes}
   */
  public abstract void clearAllSchemes();

  @NotNull
  public abstract List<T> getAllSchemes();

  @Nullable
  public abstract T findSchemeByName(@NotNull String schemeName);

  public abstract void save();

  public abstract void setCurrentSchemeName(@Nullable String schemeName);

  @Nullable
  public abstract T getCurrentScheme();

  public abstract void removeScheme(@NotNull T scheme);

  @NotNull
  public abstract Collection<String> getAllSchemeNames();

  public abstract File getRootDirectory();

  public void loadBundledScheme(@NotNull String resourceName, @NotNull Object requestor, @NotNull ThrowableConvertor<Element, T, Throwable> convertor) {
  }

  public void setSchemes(@NotNull List<T> schemes) {
    setSchemes(schemes, null);
  }

  public void setSchemes(@NotNull List<T> schemes, @Nullable Condition<T> removeCondition) {
  }
}
