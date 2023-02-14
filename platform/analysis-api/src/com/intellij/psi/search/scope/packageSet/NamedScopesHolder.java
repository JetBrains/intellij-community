// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class NamedScopesHolder implements PersistentStateComponent<Element> {
  private NamedScope[] myScopes = NamedScope.EMPTY_ARRAY;
  @NonNls private static final String SCOPE_TAG = "scope";
  @NonNls private static final String NAME_ATT = "name";
  @NonNls private static final String PATTERN_ATT = "pattern";

  protected final Project myProject;
  private VirtualFile myProjectBaseDir;

  public NamedScopesHolder(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public abstract String getDisplayName();

  public abstract Icon getIcon();

  @FunctionalInterface
  public interface ScopeListener {
    void scopesChanged();
  }

  public VirtualFile getProjectBaseDir() {
    if (myProjectBaseDir == null) {
      myProjectBaseDir = myProject.getBaseDir();
    }
    return myProjectBaseDir;
  }

  private final List<ScopeListener> myScopeListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public void addScopeListener(@NotNull ScopeListener scopeListener, @NotNull Disposable parentDisposable) {
    myScopeListeners.add(scopeListener);
    Disposer.register(parentDisposable, () -> myScopeListeners.remove(scopeListener));
  }

  public void fireScopeListeners() {
    for (ScopeListener listener : myScopeListeners) {
      listener.scopesChanged();
    }
  }

  public NamedScope @NotNull [] getScopes() {
    List<NamedScope> list = getPredefinedScopes();
    List<NamedScope> scopes = new ArrayList<>(list.size() + myScopes.length);
    scopes.addAll(list);
    Collections.addAll(scopes, myScopes);
    return scopes.toArray(NamedScope.EMPTY_ARRAY);
  }

  public NamedScope @NotNull [] getEditableScopes() {
    return myScopes;
  }

  public void removeAllSets() {
    myScopes = NamedScope.EMPTY_ARRAY;
    fireScopeListeners();
  }

  public void setScopes(NamedScope @NotNull [] scopes) {
    if (ArrayUtil.contains(null, scopes)) {
      throw new IllegalArgumentException("Must not pass null scopes, got: " + Arrays.toString(scopes));
    }
    myScopes = scopes.clone();
    fireScopeListeners();
  }

  public void addScope(@NotNull NamedScope scope) {
    myScopes = ArrayUtil.append(myScopes, scope);
    fireScopeListeners();
  }

  @Nullable
  public static NamedScope getScope(@NotNull Project project,  @NonNls String scopeName) {
    for (NamedScopesHolder holder : getAllNamedScopeHolders(project)) {
      NamedScope scope = holder.getScope(scopeName);
      if (scope != null) {
        return scope;
      }
    }
    return null;
  }

  public static NamedScopesHolder @NotNull [] getAllNamedScopeHolders(@NotNull Project project) {
    return new NamedScopesHolder[]{
      NamedScopeManager.getInstance(project),
      DependencyValidationManager.getInstance(project)
    };
  }

  @Contract("_,_,!null -> !null")
  public static NamedScopesHolder getHolder(Project project, String scopeId, NamedScopesHolder defaultHolder) {
    NamedScopesHolder[] holders = getAllNamedScopeHolders(project);
    for (NamedScopesHolder holder : holders) {
      NamedScope scope = holder.getScope(scopeId);
      if (scope != null) {
        return holder;
      }
    }
    return defaultHolder;
  }

  @NotNull
  private static Element writeScope(@NotNull NamedScope scope) {
    Element setElement = new Element(SCOPE_TAG);
    setElement.setAttribute(NAME_ATT, scope.getScopeId());
    PackageSet packageSet = scope.getValue();
    setElement.setAttribute(PATTERN_ATT, packageSet != null ? packageSet.getText() : "");
    return setElement;
  }

  @NotNull
  private NamedScope readScope(@NotNull Element setElement) {
    String name = setElement.getAttributeValue(NAME_ATT);
    PackageSet set;
    String attributeValue = setElement.getAttributeValue(PATTERN_ATT);
    try {
      set = PackageSetFactory.getInstance().compile(attributeValue);
    }
    catch (ParsingException e) {
      set = new InvalidPackageSet(attributeValue);
    }
    return createScope(name, set);
  }

  @Override
  public void loadState(@NotNull Element state) {
    List<Element> sets = state.getChildren(SCOPE_TAG);
    NamedScope [] scopes = new NamedScope[sets.size()];
    for (int i = 0; i < sets.size(); i++) {
      Element set = sets.get(i);
      scopes[i] = readScope(set);
    }
    myScopes = scopes;
    fireScopeListeners();
  }

  @Override
  @NotNull
  public Element getState() {
    Element element = new Element("state");
    for (NamedScope myScope : myScopes) {
      element.addContent(writeScope(myScope));
    }
    return element;
  }

  @Nullable
  public NamedScope getScope(@Nullable @NonNls String scopeId) {
    if (scopeId == null) return null;
    for (NamedScope scope : myScopes) {
      if (scopeId.equals(scope.getScopeId())) return scope;
    }
    return getPredefinedScope(scopeId);
  }

  @NotNull
  public List<NamedScope> getPredefinedScopes() {
    return Collections.emptyList();
  }

  @Nullable
  public NamedScope getPredefinedScope(@NotNull String name) {
    return null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public final NamedScope createScope(@NotNull String name, @Nullable PackageSet value) {
    return new NamedScope(name, () -> name, getIcon(), value);
  }
}
