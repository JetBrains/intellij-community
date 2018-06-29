// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope.packageSet;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.util.containers.ContainerUtil;
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
  private List<NamedScope> myScopes = new ArrayList<>();
  @NonNls private static final String SCOPE_TAG = "scope";
  @NonNls private static final String NAME_ATT = "name";
  @NonNls private static final String PATTERN_ATT = "pattern";

  protected final Project myProject;
  private VirtualFile myProjectBaseDir;

  public NamedScopesHolder(@NotNull Project project) {
    myProject = project;
  }

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

  public void addScopeListener(@NotNull ScopeListener scopeListener) {
    myScopeListeners.add(scopeListener);
  }

  public void removeScopeListener(@NotNull ScopeListener scopeListener) {
    myScopeListeners.remove(scopeListener);
  }

  public void fireScopeListeners() {
    for (ScopeListener listener : myScopeListeners) {
      listener.scopesChanged();
    }
  }

  @NotNull
  public NamedScope[] getScopes() {
    List<NamedScope> scopes = new ArrayList<>();
    List<NamedScope> list = getPredefinedScopes();
    scopes.addAll(list);
    scopes.addAll(myScopes);
    return scopes.toArray(new NamedScope[0]);
  }

  @NotNull
  public NamedScope[] getEditableScopes() {
    return myScopes.toArray(new NamedScope[0]);
  }

  public void removeAllSets() {
    myScopes.clear();
    fireScopeListeners();
  }

  public void setScopes(NamedScope[] scopes) {
    myScopes = new ArrayList<>(Arrays.asList(scopes));
    fireScopeListeners();
  }

  public void addScope(NamedScope scope) {
    myScopes.add(scope);
    fireScopeListeners();
  }

  @Nullable
  public static NamedScope getScope(@NotNull Project project, String scopeName) {
    for (NamedScopesHolder holder : getAllNamedScopeHolders(project)) {
      NamedScope scope = holder.getScope(scopeName);
      if (scope != null) {
        return scope;
      }
    }
    return null;
  }

  @NotNull
  public static NamedScopesHolder[] getAllNamedScopeHolders(@NotNull Project project) {
    return new NamedScopesHolder[]{
      NamedScopeManager.getInstance(project),
      DependencyValidationManager.getInstance(project)
    };
  }

  @Nullable
  public static NamedScopesHolder getHolder(Project project, String scopeName, NamedScopesHolder defaultHolder) {
    NamedScopesHolder[] holders = getAllNamedScopeHolders(project);
    for (NamedScopesHolder holder : holders) {
      NamedScope scope = holder.getScope(scopeName);
      if (scope != null) {
        return holder;
      }
    }
    return defaultHolder;
  }

  private static Element writeScope(NamedScope scope) {
    Element setElement = new Element(SCOPE_TAG);
    setElement.setAttribute(NAME_ATT, scope.getName());
    PackageSet packageSet = scope.getValue();
    setElement.setAttribute(PATTERN_ATT, packageSet != null ? packageSet.getText() : "");
    return setElement;
  }

  private NamedScope readScope(Element setElement) {
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
    myScopes.clear();
    List<Element> sets = state.getChildren(SCOPE_TAG);
    for (Element set : sets) {
      myScopes.add(readScope(set));
    }
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
  public NamedScope getScope(@Nullable String name) {
    if (name == null) return null;
    for (NamedScope scope : myScopes) {
      if (name.equals(scope.getName())) return scope;
    }
    return getPredefinedScope(name);
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
    return new NamedScope(name, getIcon(), value);
  }
}
