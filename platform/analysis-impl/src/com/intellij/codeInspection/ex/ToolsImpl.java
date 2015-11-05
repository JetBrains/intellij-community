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

/*
 * User: anna
 * Date: 15-Apr-2009
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ToolsImpl implements Tools {
  @NonNls static final String ENABLED_BY_DEFAULT_ATTRIBUTE = "enabled_by_default";
  @NonNls static final String ENABLED_ATTRIBUTE = "enabled";
  @NonNls static final String LEVEL_ATTRIBUTE = "level";

  private final String myShortName;
  private final ScopeToolState myDefaultState;
  private List<ScopeToolState> myTools;
  private boolean myEnabled;

  public ToolsImpl(@NotNull InspectionToolWrapper toolWrapper, @NotNull HighlightDisplayLevel level, boolean enabled, boolean enabledByDefault) {
    myShortName = toolWrapper.getShortName();
    myDefaultState = new ScopeToolState(CustomScopesProviderEx.getAllScope(), toolWrapper, enabledByDefault, level);
    myTools = null;
    myEnabled = enabled;
  }

  @TestOnly
  public ToolsImpl(@NotNull InspectionToolWrapper toolWrapper, @NotNull HighlightDisplayLevel level, boolean enabled) {
    this(toolWrapper, level, enabled, enabled);
  }

  @NotNull
  public ScopeToolState addTool(@NotNull NamedScope scope, @NotNull InspectionToolWrapper toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    return insertTool(scope, toolWrapper, enabled, level, myTools != null ? myTools.size() : 0);
  }

  @NotNull
  public ScopeToolState prependTool(@NotNull NamedScope scope, @NotNull InspectionToolWrapper toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    return insertTool(scope, toolWrapper, enabled, level, 0);
  }

  public ScopeToolState addTool(@NotNull String scopeName, @NotNull InspectionToolWrapper toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    return insertTool(new ScopeToolState(scopeName, toolWrapper, enabled, level), myTools != null ? myTools.size() : 0);
  }

  @NotNull
  private ScopeToolState insertTool(@NotNull NamedScope scope, @NotNull InspectionToolWrapper toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level, int idx) {
    return insertTool(new ScopeToolState(scope, toolWrapper, enabled, level), idx);
  }

  @NotNull
  private ScopeToolState insertTool(@NotNull final ScopeToolState scopeToolState, final int idx) {
    if (myTools == null) {
      myTools = new ArrayList<ScopeToolState>();
      if (scopeToolState.isEnabled()) {
        setEnabled(true);
      }
    }
    myTools.add(idx, scopeToolState);
    return scopeToolState;
  }

  @NotNull
  @Override
  public InspectionToolWrapper getInspectionTool(PsiElement element) {
    if (myTools != null) {
      final PsiFile containingFile = element == null ? null : element.getContainingFile();
      final Project project = containingFile == null ? null : containingFile.getProject();
      for (ScopeToolState state : myTools) {
        if (element == null) {
          return state.getTool();
        }
        NamedScope scope = state.getScope(project);
        if (scope != null) {
          final PackageSet packageSet = scope.getValue();
          if (packageSet != null) {
            if (containingFile != null && packageSet.contains(containingFile, DependencyValidationManager.getInstance(project))) {
              return state.getTool();
            }
          }
        }
      }

    }
    return myDefaultState.getTool();
  }

  @NotNull
  @Override
  public String getShortName() {
    return myShortName;
  }

  @NotNull
  public List<InspectionToolWrapper> getAllTools() {
    List<InspectionToolWrapper> result = new ArrayList<InspectionToolWrapper>();
    for (ScopeToolState state : getTools()) {
      InspectionToolWrapper toolWrapper = state.getTool();
      result.add(toolWrapper);
    }
    return result;
  }

  public void writeExternal(@NotNull Element inspectionElement) throws WriteExternalException {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        final Element scopeElement = new Element("scope");
        scopeElement.setAttribute("name", state.getScopeName());
        scopeElement.setAttribute(LEVEL_ATTRIBUTE, state.getLevel().getName());
        scopeElement.setAttribute(ENABLED_ATTRIBUTE, Boolean.toString(state.isEnabled()));
        InspectionToolWrapper toolWrapper = state.getTool();
        if (toolWrapper.isInitialized()) {
          toolWrapper.getTool().writeSettings(scopeElement);
        }
        inspectionElement.addContent(scopeElement);
      }
    }
    inspectionElement.setAttribute(ENABLED_ATTRIBUTE, Boolean.toString(isEnabled()));
    inspectionElement.setAttribute(LEVEL_ATTRIBUTE, getLevel().getName());
    inspectionElement.setAttribute(ENABLED_BY_DEFAULT_ATTRIBUTE, Boolean.toString(myDefaultState.isEnabled()));
    InspectionToolWrapper toolWrapper = myDefaultState.getTool();
    if (toolWrapper.isInitialized()) {
      toolWrapper.getTool().writeSettings(inspectionElement);
    }
  }

  void readExternal(@NotNull Element toolElement, @NotNull InspectionProfile profile, Map<String, List<String>> dependencies) throws InvalidDataException {
    final String levelName = toolElement.getAttributeValue(LEVEL_ATTRIBUTE);
    final ProfileManager profileManager = profile.getProfileManager();
    final SeverityRegistrar registrar = ((SeverityProvider)profileManager).getOwnSeverityRegistrar();
    HighlightDisplayLevel level = levelName != null ? HighlightDisplayLevel.find(registrar.getSeverity(levelName)) : null;
    if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
      level = HighlightDisplayLevel.WARNING;
    }
    myDefaultState.setLevel(level);
    final String enabled = toolElement.getAttributeValue(ENABLED_ATTRIBUTE);
    final boolean isEnabled = enabled != null && Boolean.parseBoolean(enabled);

    final String enabledTool = toolElement.getAttributeValue(ENABLED_BY_DEFAULT_ATTRIBUTE);
    myDefaultState.setEnabled(enabledTool != null ? Boolean.parseBoolean(enabledTool) : isEnabled);
    final InspectionToolWrapper toolWrapper = myDefaultState.getTool();

    final List scopeElements = toolElement.getChildren(ProfileEx.SCOPE);
    final List<String> scopeNames = new ArrayList<String>();
    for (Object sO : scopeElements) {
      final Element scopeElement = (Element)sO;
      final String scopeName = scopeElement.getAttributeValue(ProfileEx.NAME);
      if (scopeName == null) {
        continue;
      }
      final NamedScopesHolder scopesHolder = profileManager.getScopesManager();
      NamedScope namedScope = null;
      if (scopesHolder != null) {
        namedScope = scopesHolder.getScope(scopeName);
      }
      final String errorLevel = scopeElement.getAttributeValue(LEVEL_ATTRIBUTE);
      final String enabledInScope = scopeElement.getAttributeValue(ENABLED_ATTRIBUTE);
      final InspectionToolWrapper copyToolWrapper = toolWrapper.createCopy();
    // check if unknown children exists
      if (scopeElement.getAttributes().size() > 3 || !scopeElement.getChildren().isEmpty()) {
        copyToolWrapper.getTool().readSettings(scopeElement);
      }
      HighlightDisplayLevel scopeLevel = errorLevel != null ?
                                         HighlightDisplayLevel.find(registrar.getSeverity(errorLevel)) : null;
      if (scopeLevel == null) {
        scopeLevel = level;
      }
      if (namedScope != null) {
        addTool(namedScope, copyToolWrapper, enabledInScope != null && Boolean.parseBoolean(enabledInScope), scopeLevel);
      }
      else {
        addTool(scopeName, copyToolWrapper, enabledInScope != null && Boolean.parseBoolean(enabledInScope), scopeLevel);
      }

      scopeNames.add(scopeName);
    }

    for (int i = 0; i < scopeNames.size(); i++) {
      String scopeName = scopeNames.get(i);
      List<String> order = dependencies.get(scopeName);
      if (order == null) {
        order = new ArrayList<String>();
        dependencies.put(scopeName, order);
      }
      for (int j = i + 1; j < scopeNames.size(); j++) {
        order.add(scopeNames.get(j));
      }
    }

    // check if unknown children exists
    if (toolElement.getAttributes().size() > 4 || toolElement.getChildren().size() > scopeElements.size()) {
      toolWrapper.getTool().readSettings(toolElement);
    }

    myEnabled = isEnabled;
  }

  @NotNull
  @Override
  public InspectionToolWrapper getTool() {
    if (myTools == null) return myDefaultState.getTool();
    return myTools.iterator().next().getTool();
  }

  @Override
  @NotNull
  public List<ScopeToolState> getTools() {
    if (myTools == null) return Collections.singletonList(myDefaultState);
    List<ScopeToolState> result = new ArrayList<ScopeToolState>(myTools);
    result.add(myDefaultState);
    return result;
  }

  @Override
  @NotNull
  public ScopeToolState getDefaultState() {
    return myDefaultState;
  }

  public void removeScope(int scopeIdx) {
    if (myTools != null && scopeIdx >= 0 && myTools.size() > scopeIdx) {
      myTools.remove(scopeIdx);
      checkToolsIsEmpty();
    }
  }

  public void removeScope(@NotNull final String scopeName) {
    if (myTools != null) {
      for (ScopeToolState tool : myTools) {
        if (scopeName.equals(tool.getScopeName())) {
          myTools.remove(tool);
          break;
        }
      }
      checkToolsIsEmpty();
    }
  }

  private void checkToolsIsEmpty() {
    if (myTools.isEmpty()) {
      myTools = null;
      setEnabled(myDefaultState.isEnabled());
    }
  }

  public void removeAllScopes() {
    myTools = null;
  }

  public void setScope(int idx, NamedScope namedScope) {
    if (myTools != null && myTools.size() > idx && idx >= 0) {
      final ScopeToolState scopeToolState = myTools.get(idx);
      InspectionToolWrapper toolWrapper = scopeToolState.getTool();
      myTools.remove(idx);
      myTools.add(idx, new ScopeToolState(namedScope, toolWrapper, scopeToolState.isEnabled(), scopeToolState.getLevel()));
    }
  }

  public void moveScope(int idx, int dir) {
    if (myTools != null && idx >= 0 && idx < myTools.size() && idx + dir >= 0 && idx + dir < myTools.size()) {
      final ScopeToolState state = myTools.get(idx);
      myTools.set(idx, myTools.get(idx + dir));
      myTools.set(idx + dir, state);
    }
  }

  public boolean isEnabled(NamedScope namedScope, Project project) {
    if (!myEnabled) return false;
    if (namedScope != null && myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(namedScope, state.getScope(project))) return state.isEnabled();
      }
    }
    return myDefaultState.isEnabled();
  }

  public HighlightDisplayLevel getLevel(PsiElement element) {
    if (myTools == null || element == null) return myDefaultState.getLevel();
    final Project project = element.getProject();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
    for (ScopeToolState state : myTools) {
      final NamedScope scope = state.getScope(project);
      final PackageSet set = scope != null ? scope.getValue() : null;
      if (set != null && set.contains(element.getContainingFile(), manager)) {
        return state.getLevel();
      }
    }
    return myDefaultState.getLevel();
  }



  public HighlightDisplayLevel getLevel() {
    return myDefaultState.getLevel();
  }

  @Override
  public boolean isEnabled() {
    return myEnabled;
  }


  @Override
  public boolean isEnabled(PsiElement element) {
    if (!myEnabled) return false;
    if (myTools == null || element == null) return myDefaultState.isEnabled();
    final Project project = element.getProject();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
    for (ScopeToolState state : myTools) {
      final NamedScope scope = state.getScope(project);
      if (scope != null) {
        final PackageSet set = scope.getValue();
        if (set != null && set.contains(element.getContainingFile(), manager)) {
          return state.isEnabled();
        }
      }
    }
    return myDefaultState.isEnabled();
  }

  @Override
  @Nullable
  public InspectionToolWrapper getEnabledTool(PsiElement element) {
    if (!myEnabled) return null;
    if (myTools == null || element == null) {
      return myDefaultState.isEnabled() ? myDefaultState.getTool() : null;
    }
    final Project project = element.getProject();
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
    for (ScopeToolState state : myTools) {
      final NamedScope scope = state.getScope(project);
      if (scope != null) {
        final PackageSet set = scope.getValue();
        if (set != null && set.contains(element.getContainingFile(), manager)) {
          return state.isEnabled() ? state.getTool() : null;
        }
      }
    }
    return myDefaultState.isEnabled() ? myDefaultState.getTool() : null;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void enableTool(NamedScope namedScope, Project project) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(project), namedScope)) {
          state.setEnabled(true);
        }
      }
    }
    setEnabled(true);
  }

  public void disableTool(NamedScope namedScope, Project project) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(project), namedScope)) {
          state.setEnabled(false);
        }
      }
    }
  }


  public void disableTool(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(project);
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        final NamedScope scope = state.getScope(project);
        if (scope != null) {
          final PackageSet packageSet = scope.getValue();
          if (packageSet != null) {
            final PsiFile file = element.getContainingFile();
            if (file != null) {
              if (packageSet.contains(file, validationManager)) {
                state.setEnabled(false);
                return;
              }
            }
            else {
              if (packageSet instanceof PackageSetBase &&
                  ((PackageSetBase)packageSet).contains(PsiUtilCore.getVirtualFile(element), project, validationManager)) {
                state.setEnabled(false);
                return;
              }
            }
          }
        }
      }
      myDefaultState.setEnabled(false);
    }
    else {
      myDefaultState.setEnabled(false);
      setEnabled(false);
    }
  }

  @NotNull
  public HighlightDisplayLevel getLevel(final NamedScope scope, Project project) {
    if (myTools != null && scope != null){
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(project), scope)) {
          return state.getLevel();
        }
      }
    }
    return myDefaultState.getLevel();
  }

   @Override
   public boolean equals(Object o) {
     ToolsImpl tools = (ToolsImpl)o;
      if (myEnabled != tools.myEnabled) return false;
      if (getTools().size() != tools.getTools().size()) return false;
      for (int i = 0; i < getTools().size(); i++) {
        final ScopeToolState state = getTools().get(i);
        final ScopeToolState toolState = tools.getTools().get(i);
        if (!state.equalTo(toolState)){
          return false;
        }
      }
      return true;

  }

  public void setLevel(@NotNull HighlightDisplayLevel level, @Nullable String scopeName, Project project) {
    if (scopeName == null) {
      myDefaultState.setLevel(level);
    } else {
      if (myTools == null) {
        return;
      }
      ScopeToolState scopeToolState = null;
      int index = -1;
      for (int i = 0; i < myTools.size(); i++) {
        ScopeToolState tool = myTools.get(i);
        if (scopeName.equals(tool.getScopeName())) {
          scopeToolState = tool;
          myTools.remove(tool);
          index = i;
          break;
        }
      }
      if (index < 0) {
        throw new IllegalStateException("Scope " + scopeName + " not found");
      }
      final InspectionToolWrapper toolWrapper = scopeToolState.getTool();
      final NamedScope scope = scopeToolState.getScope(project);
      if (scope != null) {
        myTools.add(index, new ScopeToolState(scope, toolWrapper, scopeToolState.isEnabled(), level));
      }
      else {
        myTools.add(index, new ScopeToolState(scopeToolState.getScopeName(), toolWrapper, scopeToolState.isEnabled(), level));
      }
    }
  }

  public void setDefaultState(@NotNull InspectionToolWrapper toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    myDefaultState.setTool(toolWrapper);
    myDefaultState.setLevel(level);
    myDefaultState.setEnabled(enabled);
  }

  public void setLevel(@NotNull HighlightDisplayLevel level) {
    myDefaultState.setLevel(level);
  }

  @Nullable
  public List<ScopeToolState> getNonDefaultTools() {
    return myTools;
  }
}