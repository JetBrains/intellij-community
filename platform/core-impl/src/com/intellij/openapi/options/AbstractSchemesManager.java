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
package com.intellij.openapi.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.UniqueFileNamesProvider;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.UniqueNameGenerator;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public abstract class AbstractSchemesManager<T extends Scheme, E extends ExternalizableScheme> implements SchemesManager<T,E> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.AbstractSchemesManager");

  protected final List<T> mySchemes = new ArrayList<T>();
  private volatile T myCurrentScheme;
  private String myCurrentSchemeName;

  @Override
  public void addNewScheme(@NotNull final T scheme, final boolean replaceExisting) {
    int toReplace = -1;
    boolean newSchemeIsShared = isShared(scheme);

    for (int i = 0; i < mySchemes.size(); i++) {
      T t = mySchemes.get(i);
      if (Comparing.equal(scheme.getName(),t.getName()) && newSchemeIsShared == isShared(t)) {
        toReplace = i;
        break;
      }
    }
    if (toReplace == -1) {
      mySchemes.add(scheme);
    }
    else {
      if (replaceExisting || !isExternalizable(scheme)) {
        mySchemes.set(toReplace, scheme);
      }
      else {
        renameScheme((E)scheme, generateUniqueName(scheme));
        mySchemes.add(scheme);
      }
    }
    onSchemeAdded(scheme);
    checkCurrentScheme(scheme);
  }

  protected void checkCurrentScheme(final Scheme scheme) {
    if (myCurrentScheme == null && myCurrentSchemeName != null && myCurrentSchemeName.equals(scheme.getName())) {
      myCurrentScheme = (T)scheme;
    }
  }

  private String generateUniqueName(final T scheme) {
    return UniqueNameGenerator.generateUniqueName(UniqueFileNamesProvider.convertName(scheme.getName()), collectExistingNames(mySchemes));
  }

  private Collection<String> collectExistingNames(final Collection<T> schemes) {
    HashSet<String> result = new HashSet<String>();
    for (T scheme : schemes) {
      result.add(scheme.getName());
    }
    return result;
  }

  @Override
  public void clearAllSchemes() {
    for (T t : getAllSchemes()) {
      removeScheme(t);
    }
  }

  @Override
  @NotNull
  public List<T> getAllSchemes() {
    return Collections.unmodifiableList(new ArrayList<T>(mySchemes));
  }

  @Override
  @Nullable
  public T findSchemeByName(final String schemeName) {
    for (T scheme : mySchemes) {
      if (Comparing.equal(scheme.getName(),schemeName)) {
        return scheme;
      }
    }

    return null;
  }

  @Override
  public abstract void save() throws WriteExternalException;

  @Override
  public void setCurrentSchemeName(final String schemeName) {
    myCurrentSchemeName = schemeName;
    myCurrentScheme = schemeName == null ? null : findSchemeByName(schemeName);
  }

  @Override
  @Nullable
  public T getCurrentScheme() {
    T currentScheme = myCurrentScheme;
    if (currentScheme == null) {
      return null;
    }
    return findSchemeByName(currentScheme.getName());
  }

  @Override
  public void removeScheme(final T scheme) {
    String schemeName = scheme.getName();
    Scheme toDelete = findSchemeToDelete(schemeName);

    //noinspection SuspiciousMethodCalls
    mySchemes.remove(toDelete);
    if (myCurrentScheme == toDelete) {
      myCurrentScheme = null;
    }

    onSchemeDeleted(toDelete);
  }

  protected abstract void onSchemeDeleted(final Scheme toDelete);

  private Scheme findSchemeToDelete(final String schemeName) {
    for (T scheme : mySchemes) {
      if (Comparing.equal(schemeName,scheme.getName())) return scheme;
    }

    return null;
  }


  @Override
  @NotNull
  public Collection<String> getAllSchemeNames() {
    return getAllSchemeNames(mySchemes);
  }

  public Collection<String> getAllSchemeNames(Collection<T> schemes) {
    Set<String> names = new THashSet<String>();
    for (T scheme : schemes) {
      names.add(scheme.getName());
    }
    return names;
  }

  protected abstract void onSchemeAdded(final T scheme);

  protected void renameScheme(final E scheme, String newName){
    if (!Comparing.equal(newName,scheme.getName())) {
      scheme.setName(newName);
      LOG.assertTrue(Comparing.equal(newName,scheme.getName()));
    }
  }

  @Override
  @NotNull
  public Collection<SharedScheme<E>> loadSharedSchemes() {
    return loadSharedSchemes(getAllSchemes());
  }

  protected boolean isExternalizable(final T scheme) {
    return scheme instanceof ExternalizableScheme;
  }
}
