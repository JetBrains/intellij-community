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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractSchemesManager<T extends Scheme, E extends ExternalizableScheme> extends SchemesManager<T, E> {
  private static final Logger LOG = Logger.getInstance(AbstractSchemesManager.class);

  protected final ArrayList<T> mySchemes = new ArrayList<T>();
  protected volatile T myCurrentScheme;
  protected String myCurrentSchemeName;

  @Override
  public void addNewScheme(@NotNull T scheme, boolean replaceExisting) {
    int toReplace = -1;
    for (int i = 0; i < mySchemes.size(); i++) {
      T existingScheme = mySchemes.get(i);
      if (existingScheme.getName().equals(scheme.getName())) {
        toReplace = i;
        if (replaceExisting && existingScheme instanceof ExternalizableScheme && scheme instanceof ExternalizableScheme) {
          swapInfo((ExternalizableScheme)scheme, (ExternalizableScheme)existingScheme);
        }
        break;
      }
    }
    if (toReplace == -1) {
      mySchemes.add(scheme);
    }
    else if (replaceExisting || !(scheme instanceof ExternalizableScheme)) {
      mySchemes.set(toReplace, scheme);
    }
    else {
      //noinspection unchecked
      renameScheme((ExternalizableScheme)scheme, UniqueNameGenerator.generateUniqueName(scheme.getName(), collectExistingNames(mySchemes)));
      mySchemes.add(scheme);
    }

    schemeAdded(scheme);
    checkCurrentScheme(scheme);
  }

  protected void swapInfo(@NotNull ExternalizableScheme scheme, @NotNull ExternalizableScheme existingScheme) {
  }

  protected void checkCurrentScheme(@NotNull Scheme scheme) {
    if (myCurrentScheme == null && scheme.getName().equals(myCurrentSchemeName)) {
      //noinspection unchecked
      myCurrentScheme = (T)scheme;
    }
  }

  @NotNull
  private Collection<String> collectExistingNames(@NotNull Collection<T> schemes) {
    Set<String> result = new THashSet<String>(schemes.size());
    for (T scheme : schemes) {
      result.add(scheme.getName());
    }
    return result;
  }

  @Override
  public void clearAllSchemes() {
    doRemoveAll();
  }

  protected void doRemoveAll() {
    for (T myScheme : mySchemes) {
      schemeDeleted(myScheme);
    }
    mySchemes.clear();
  }

  @Override
  @NotNull
  public List<T> getAllSchemes() {
    return Collections.unmodifiableList(mySchemes);
  }

  @Override
  @Nullable
  public T findSchemeByName(@NotNull String schemeName) {
    for (T scheme : mySchemes) {
      if (scheme.getName().equals(schemeName)) {
        return scheme;
      }
    }
    return null;
  }

  @Override
  public void setCurrentSchemeName(@Nullable String schemeName) {
    myCurrentSchemeName = schemeName;
    myCurrentScheme = schemeName == null ? null : findSchemeByName(schemeName);
  }

  @Override
  @Nullable
  public T getCurrentScheme() {
    T currentScheme = myCurrentScheme;
    return currentScheme == null ? null : findSchemeByName(currentScheme.getName());
  }

  @Override
  public void removeScheme(@NotNull T scheme) {
    for (int i = 0, n = mySchemes.size(); i < n; i++) {
      T s = mySchemes.get(i);
      if (scheme.getName().equals(s.getName())) {
        schemeDeleted(s);
        mySchemes.remove(i);
        break;
      }
    }
  }

  protected void schemeDeleted(@NotNull Scheme scheme) {
    if (myCurrentScheme == scheme) {
      myCurrentScheme = null;
    }
  }

  @Override
  @NotNull
  public Collection<String> getAllSchemeNames() {
    List<String> names = new ArrayList<String>(mySchemes.size());
    for (T scheme : mySchemes) {
      names.add(scheme.getName());
    }
    return names;
  }

  protected abstract void schemeAdded(@NotNull T scheme);

  protected static void renameScheme(@NotNull ExternalizableScheme scheme, @NotNull String newName) {
    if (!newName.equals(scheme.getName())) {
      scheme.setName(newName);
      LOG.assertTrue(newName.equals(scheme.getName()));
    }
  }
}
