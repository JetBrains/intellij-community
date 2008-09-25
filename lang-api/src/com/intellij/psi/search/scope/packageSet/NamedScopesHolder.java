/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class NamedScopesHolder implements PersistentStateComponent<Element> {
  private List<NamedScope> myScopes = new ArrayList<NamedScope>();
  @NonNls private static final String SCOPE_TAG = "scope";
  @NonNls private static final String NAME_ATT = "name";
  @NonNls private static final String PATTERN_ATT = "pattern";

  public abstract String getDisplayName();
  public abstract Icon getIcon();

  public static interface ScopeListener {
    void scopesChanged();
  }
  private List<ScopeListener> myScopeListeners;
  public synchronized void addScopeListener(ScopeListener scopeListener) {
    if (myScopeListeners == null) {
      myScopeListeners = new ArrayList<ScopeListener>();
    }
    myScopeListeners.add(scopeListener);
  }
  public synchronized void removeScopeListener(ScopeListener scopeListener) {
    myScopeListeners.remove(scopeListener);
  }

  public synchronized void fireScopeListeners() {
    if (myScopeListeners != null) {
      for (ScopeListener listener : myScopeListeners) {
        listener.scopesChanged();
      }
    }
  }

  @NotNull public NamedScope[] getScopes() {
    final List<NamedScope> scopes = new ArrayList<NamedScope>();
    List<NamedScope> list = getPredefinedScopes();
    scopes.addAll(list);
    scopes.addAll(myScopes);
    return scopes.toArray(new NamedScope[scopes.size()]);
  }

  public NamedScope[] getEditableScopes(){
    return myScopes.toArray(new NamedScope[myScopes.size()]);
  }

  public void removeAllSets() {
    myScopes.clear();
    fireScopeListeners();
  }

  public void setScopes(NamedScope[] scopes) {
    myScopes = new ArrayList<NamedScope>(Arrays.asList(scopes));
    fireScopeListeners();
  }

  public void addScope(NamedScope scope) {
    myScopes.add(scope);
    fireScopeListeners();
  }

  @Nullable
  public static NamedScope getScope(final Project project, final String scopeName) {
    final NamedScopesHolder[] holders = project.getComponents(NamedScopesHolder.class);
    for (NamedScopesHolder holder : holders) {
      final NamedScope scope = holder.getScope(scopeName);
      if (scope != null) {
        return scope;
      }
    }
    return null;
  }

  @Nullable
  public static NamedScopesHolder getHolder(final Project project, final String scopeName, final NamedScopesHolder defaultHolder) {
    final NamedScopesHolder[] holders = project.getComponents(NamedScopesHolder.class);
    for (NamedScopesHolder holder : holders) {
      final NamedScope scope = holder.getScope(scopeName);
      if (scope != null) {
        return holder;
      }
    }
    return defaultHolder;
  }

  private static Element writeScope(NamedScope scope) {
    Element setElement = new Element(SCOPE_TAG);
    setElement.setAttribute(NAME_ATT, scope.getName());
    final PackageSet packageSet = scope.getValue();
    setElement.setAttribute(PATTERN_ATT, packageSet != null ? packageSet.getText() : "");
    return setElement;
  }

  private static NamedScope readScope(Element setElement){
    String name = setElement.getAttributeValue(NAME_ATT);
    PackageSet set = null;
    try {
      set = PackageSetFactory.getInstance().compile(setElement.getAttributeValue(PATTERN_ATT));
    }
    catch (ParsingException e) {
      // Skip damaged set
    }
    return new NamedScope(name, set);
  }

  public void loadState(final Element state) {
    List sets = state.getChildren(SCOPE_TAG);
    for (Object set : sets) {
      addScope(readScope((Element)set));
    }
    fireScopeListeners();
  }

  public Element getState() {
    Element element = new Element("state");
    for (NamedScope myScope : myScopes) {
      element.addContent(writeScope(myScope));
    }
    return element;
  }

  @Nullable
  public NamedScope getScope(@Nullable String name) {
    if (name == null) return null;
    for (NamedScope scope : myScopes) {
      if (name.equals(scope.getName())) return scope;
    }
    final List<NamedScope> predefinedScopes = getPredefinedScopes();
    for (NamedScope scope : predefinedScopes) {
      if (name.equals(scope.getName())) return scope;
    }
    return null;
  }

  @NotNull
  public List<NamedScope> getPredefinedScopes(){
    return Collections.emptyList();
  }
}
