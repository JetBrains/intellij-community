// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.ProfileEx;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ToolsImpl implements Tools {
  @NonNls static final String ENABLED_BY_DEFAULT_ATTRIBUTE = "enabled_by_default";
  @NonNls static final String ENABLED_ATTRIBUTE = "enabled";
  @NonNls static final String LEVEL_ATTRIBUTE = "level";

  private final String myShortName;
  private final ScopeToolState myDefaultState;
  private List<ScopeToolState> myTools;
  private boolean myEnabled;

  ToolsImpl(@NotNull InspectionToolWrapper<?,?> toolWrapper, @NotNull HighlightDisplayLevel level, boolean enabled, boolean enabledByDefault) {
    myShortName = toolWrapper.getShortName();
    myDefaultState = new ScopeToolState(CustomScopesProviderEx.getAllScope(), toolWrapper, enabledByDefault, level);
    myTools = null;
    myEnabled = enabled;
  }

  @NotNull
  public ScopeToolState addTool(@NotNull NamedScope scope, @NotNull InspectionToolWrapper<?,?> toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    return insertTool(scope, toolWrapper, enabled, level, myTools != null ? myTools.size() : 0);
  }

  public @NotNull ScopeToolState prependTool(@NotNull NamedScope scope,
                                      @NotNull InspectionToolWrapper<?,?> toolWrapper,
                                      boolean enabled,
                                      @NotNull HighlightDisplayLevel level) {
    return insertTool(scope, toolWrapper, enabled, level, 0);
  }

  @NotNull
  public ScopeToolState addTool(@NotNull String scopeName, @NotNull InspectionToolWrapper<?,?> toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    return insertTool(new ScopeToolState(scopeName, toolWrapper, enabled, level), myTools != null ? myTools.size() : 0);
  }

  @NotNull
  private ScopeToolState insertTool(@NotNull NamedScope scope, @NotNull InspectionToolWrapper<?,?> toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level, int idx) {
    return insertTool(new ScopeToolState(scope, toolWrapper, enabled, level), idx);
  }

  @NotNull
  private ScopeToolState insertTool(@NotNull ScopeToolState scopeToolState, int idx) {
    if (myTools == null) {
      myTools = new ArrayList<>();
      if (scopeToolState.isEnabled()) {
        setEnabled(true);
      }
    }
    myTools.add(idx, scopeToolState);
    return scopeToolState;
  }

  @NotNull
  @Override
  public InspectionToolWrapper<?,?> getInspectionTool(@Nullable PsiElement element) {
    if (myTools != null) {
      Project project = element == null ? null : element.getProject();
      PsiFile containingFile = element == null ? null : InjectedLanguageManager.getInstance(project).getTopLevelFile(element);
      for (ScopeToolState state : myTools) {
        if (element == null) {
          return state.getTool();
        }
        NamedScope scope = state.getScope(project);
        if (scope != null) {
          PackageSet packageSet = scope.getValue();
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

  void cleanupTools(@NotNull Project project) {
    for (ScopeToolState state : getTools()) {
      state.getTool().cleanup(project);
    }
  }

  void scopesChanged() {
    if (myTools != null) {
      for (ScopeToolState tool : myTools) {
        tool.scopesChanged();
      }
    }
    myDefaultState.scopesChanged();
  }

  public void writeExternal(@NotNull Element inspectionElement) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        Element scopeElement = new Element("scope");
        scopeElement.setAttribute("name", state.getScopeName());
        scopeElement.setAttribute(LEVEL_ATTRIBUTE, state.getLevel().getName());
        scopeElement.setAttribute(ENABLED_ATTRIBUTE, Boolean.toString(state.isEnabled()));
        String keyExternalName = state.getEditorAttributesExternalName();
        if (keyExternalName != null) {
          scopeElement.setAttribute("editorAttributes", keyExternalName);
        }
        InspectionToolWrapper<?,?> toolWrapper = state.getTool();
        if (toolWrapper.isInitialized()) {
          toolWrapper.getTool().writeSettings(scopeElement);
        }
        inspectionElement.addContent(scopeElement);
      }
    }
    inspectionElement.setAttribute(ENABLED_ATTRIBUTE, Boolean.toString(isEnabled()));
    inspectionElement.setAttribute(LEVEL_ATTRIBUTE, getLevel().getName());
    inspectionElement.setAttribute(ENABLED_BY_DEFAULT_ATTRIBUTE, Boolean.toString(myDefaultState.isEnabled()));
    @Nullable String attributesKey = myDefaultState.getEditorAttributesExternalName();
    if (attributesKey != null) {
      inspectionElement.setAttribute("editorAttributes", attributesKey);
    }
    InspectionToolWrapper<?,?> toolWrapper = myDefaultState.getTool();
    if (toolWrapper.isInitialized()) {
      ScopeToolState.tryWriteSettings(toolWrapper.getTool(), inspectionElement);
    }
  }

  void readExternal(@NotNull Element toolElement, @NotNull InspectionProfileManager profileManager, @Nullable Map<? super String, List<String>> dependencies) {
    String levelName = toolElement.getAttributeValue(LEVEL_ATTRIBUTE);
    SeverityRegistrar registrar = profileManager.getSeverityRegistrar();
    HighlightDisplayLevel level;
    HighlightSeverity severity = levelName == null ? null : registrar.getSeverity(levelName);
    level = severity == null ? null : HighlightDisplayLevel.find(severity);
    if (level == null) {
      level = HighlightDisplayLevel.WARNING;
    }
    myDefaultState.setLevel(level);
    InspectionToolWrapper<?,?> toolWrapper = myDefaultState.getTool();
    String editorAttributes = toolElement.getAttributeValue("editorAttributes");
    if (editorAttributes != null) {
      myDefaultState.setEditorAttributesExternalName(editorAttributes);
    }
    String enabled = toolElement.getAttributeValue(ENABLED_ATTRIBUTE);
    boolean isEnabled = Boolean.parseBoolean(enabled);

    String enabledTool = toolElement.getAttributeValue(ENABLED_BY_DEFAULT_ATTRIBUTE);
    myDefaultState.setEnabled(enabledTool != null ? Boolean.parseBoolean(enabledTool) : isEnabled);

    List<Element> scopeElements = toolElement.getChildren(ProfileEx.SCOPE);
    if (!scopeElements.isEmpty()) {
      List<String> scopeNames = new SmartList<>();
      for (Element scopeElement : scopeElements) {
        String scopeName = scopeElement.getAttributeValue(ProfileEx.NAME);
        if (scopeName == null) {
          continue;
        }
        NamedScopesHolder scopesHolder = profileManager.getScopesManager();
        NamedScope namedScope = null;
        if (scopesHolder != null) {
          namedScope = scopesHolder.getScope(scopeName);
        }
        String errorLevel = scopeElement.getAttributeValue(LEVEL_ATTRIBUTE);
        String enabledInScope = scopeElement.getAttributeValue(ENABLED_ATTRIBUTE);
        InspectionToolWrapper<?,?> copyToolWrapper = toolWrapper.createCopy();
        // check if unknown children exists
        if (scopeElement.getAttributes().size() > 3 || !scopeElement.getChildren().isEmpty()) {
          copyToolWrapper.getTool().readSettings(scopeElement);
        }
        HighlightSeverity errorSeverity = errorLevel == null ? null : registrar.getSeverity(errorLevel);
        HighlightDisplayLevel scopeLevel = errorSeverity == null ? null : HighlightDisplayLevel.find(errorSeverity);
        if (scopeLevel == null) {
          scopeLevel = level;
        }
        ScopeToolState state;
        if (namedScope != null) {
          state = addTool(namedScope, copyToolWrapper, Boolean.parseBoolean(enabledInScope), scopeLevel);
        }
        else {
          state = addTool(scopeName, copyToolWrapper, Boolean.parseBoolean(enabledInScope), scopeLevel);
        }

        editorAttributes = scopeElement.getAttributeValue("editorAttributes");
        if (editorAttributes != null) {
          state.setEditorAttributesExternalName(editorAttributes);
        }
        scopeNames.add(scopeName);
      }

      if (dependencies != null) {
        for (int i = 0; i < scopeNames.size(); i++) {
          String scopeName = scopeNames.get(i);
          List<String> order = dependencies.computeIfAbsent(scopeName, __ -> new ArrayList<>());
          for (int j = i + 1; j < scopeNames.size(); j++) {
            order.add(scopeNames.get(j));
          }
        }
      }
    }

    // check if unknown children exists
    if (toolElement.getAttributes().size() > 4 || toolElement.getChildren().size() > scopeElements.size()) {
      ScopeToolState.tryReadSettings(toolWrapper.getTool(), toolElement);
    }

    myEnabled = isEnabled;
  }

  /**
   * Warning: Usage of this method is discouraged as if separate tool options are defined for different scopes, it just returns
   * the options for the first scope which may lead to unexpected results. Consider using {@link #getInspectionTool(PsiElement)} instead.
   *
   * @return an InspectionToolWrapper associated with this tool.
   */
  @NotNull
  @Override
  public InspectionToolWrapper<?,?> getTool() {
    if (myTools == null) return myDefaultState.getTool();
    return myTools.iterator().next().getTool();
  }

  @Override
  @NotNull
  public List<ScopeToolState> getTools() {
    if (myTools == null) {
      return Collections.singletonList(myDefaultState);
    }

    List<ScopeToolState> result = new ArrayList<>(myTools);
    result.add(myDefaultState);
    return result;
  }

  void changeToolsOrder(@NotNull List<String> scopesOrder) {
    if (myTools != null) {
      myTools.sort((scope1, scope2) -> {
        int key1 = scopesOrder.indexOf(scope1.getScopeName());
        int key2 = scopesOrder.indexOf(scope2.getScopeName());

        if (key1 == -1 && key2 == -1) return scope1.getScopeName().compareTo(scope2.getScopeName());
        return Integer.compare(key1, key2);
      });
    }
  }

  @Override
  public void collectTools(@NotNull List<? super ScopeToolState> result) {
    if (myTools != null) {
      result.addAll(myTools);
    }
    result.add(myDefaultState);
  }

  @Override
  @NotNull
  public ScopeToolState getDefaultState() {
    return myDefaultState;
  }

  void setDefaultEnabled(boolean isEnabled) {
    getDefaultState().setEnabled(isEnabled);
    if (isEnabled) {
      setEnabled(true);
    }
    else {
      disableWholeToolIfCan();
    }
  }

  public void removeScope(@NotNull String scopeName) {
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

  public void setScope(int idx, @NotNull NamedScope namedScope) {
    if (myTools != null && myTools.size() > idx && idx >= 0) {
      ScopeToolState scopeToolState = myTools.get(idx);
      InspectionToolWrapper<?,?> toolWrapper = scopeToolState.getTool();
      myTools.remove(idx);
      myTools.add(idx, new ScopeToolState(namedScope, toolWrapper, scopeToolState.isEnabled(), scopeToolState.getLevel()));
    }
  }

  boolean isEnabled(NamedScope namedScope, Project project) {
    if (!myEnabled) return false;
    if (namedScope != null && myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(namedScope, state.getScope(project))) return state.isEnabled();
      }
    }
    return myDefaultState.isEnabled();
  }

  @NotNull
  public HighlightDisplayLevel getLevel(PsiElement element) {
    return getState(element).getLevel();
  }
  
  public ScopeToolState getState(PsiElement element) {
    if (myTools == null || element == null) return myDefaultState;
    return ReadAction.compute(() -> {
      if (!element.isValid()) return myDefaultState;
      
      Project project = element.getProject();
      DependencyValidationManager manager = DependencyValidationManager.getInstance(project);
      for (ScopeToolState state : myTools) {
        NamedScope scope = state.getScope(project);
        PackageSet set = scope != null ? scope.getValue() : null;
        if (set != null && set.contains(element.getContainingFile(), manager)) {
          return state;
        }
      }
      return myDefaultState;
    });
  }
  
  @Nullable
  public TextAttributesKey getAttributesKey(PsiElement element) {
    return getState(element).getEditorAttributesKey();
  }

  @NotNull
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
    return getState(element).isEnabled();
  }

  @Nullable
  @Override
  public InspectionToolWrapper<?,?> getEnabledTool(@Nullable PsiElement element, boolean includeDoNotShow) {
    if (!myEnabled) return null;
    ScopeToolState state = getState(element);
    return state.isEnabled() && (includeDoNotShow || isAvailableInBatch(state)) ? state.getTool() : null;
  }

  private static boolean isAvailableInBatch(ScopeToolState state) {
    HighlightDisplayLevel level = state.getLevel();
    return !(HighlightDisplayLevel.DO_NOT_SHOW.equals(level) || HighlightDisplayLevel.CONSIDERATION_ATTRIBUTES.equals(level));
  }

  @Nullable
  @Override
  public InspectionToolWrapper<?,?> getEnabledTool(@Nullable PsiElement element) {
    return getEnabledTool(element, true);
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void enableTool(@NotNull NamedScope namedScope, Project project) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (namedScope.equals(state.getScope(project))) {
          state.setEnabled(true);
        }
      }
    }
    setEnabled(true);
  }

  void disableTool(@NotNull NamedScope namedScope, Project project) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(project), namedScope)) {
          state.setEnabled(false);
        }
      }
      disableWholeToolIfCan();
    }
  }

  public void disableTool(@NotNull PsiElement element) {
    Project project = element.getProject();
    DependencyValidationManager validationManager = DependencyValidationManager.getInstance(project);
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        NamedScope scope = state.getScope(project);
        if (scope != null) {
          PackageSet packageSet = scope.getValue();
          if (packageSet != null) {
            PsiFile file = element.getContainingFile();
            if (file != null) {
              if (packageSet.contains(file, validationManager)) {
                state.setEnabled(false);
                return;
              }
            }
            else {
              VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
              if (packageSet instanceof PackageSetBase && virtualFile != null &&
                  ((PackageSetBase)packageSet).contains(virtualFile, project, validationManager)) {
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
  public HighlightDisplayLevel getLevel(NamedScope scope, Project project) {
    if (myTools != null && scope != null){
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(project), scope)) {
          return state.getLevel();
        }
      }
    }
    return myDefaultState.getLevel();
  }

  @NotNull
  public HighlightDisplayLevel getLevel(String scope, Project project) {
    if (myTools != null && scope != null){
      for (ScopeToolState state : myTools) {
        final NamedScope stateScope = state.getScope(project);
        if (stateScope != null && scope.equals(stateScope.getScopeId())) {
          return state.getLevel();
        }
      }
    }
    return myDefaultState.getLevel();
  }

  @Nullable
  public TextAttributesKey getEditorAttributesKey(NamedScope scope, Project project) {
    if (myTools != null && scope != null) {
      for (ScopeToolState state : myTools) {
        if (Objects.equals(state.getScope(project), scope)) {
          return state.getEditorAttributesKey();
        }
      }
    }
    return myDefaultState.getEditorAttributesKey();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ToolsImpl)) return false;
    ToolsImpl tools = (ToolsImpl)o;
    if (myEnabled != tools.myEnabled) return false;
    if (getTools().size() != tools.getTools().size()) return false;
    for (int i = 0; i < getTools().size(); i++) {
      ScopeToolState state = getTools().get(i);
      ScopeToolState toolState = tools.getTools().get(i);
      if (!state.equalTo(toolState)) {
        return false;
      }
    }
    return true;
  }


  public void setEditorAttributesKey(@Nullable String externalName, @Nullable String scopeName) {
    if (scopeName == null) {
      myDefaultState.setEditorAttributesExternalName(externalName);
    }
    else {
      if (myTools == null) return;
      for (ScopeToolState tool : myTools) {
        if (scopeName.equals(tool.getScopeName())) {
          tool.setEditorAttributesExternalName(externalName);
          break;
        }
      }
    }
  }
  
  public void setLevel(HighlightDisplayLevel level, PsiElement element) {
    getState(element).setLevel(level);
  }
  
  public void setLevel(@NotNull HighlightDisplayLevel level, @Nullable String scopeName, Project project) {
    if (scopeName == null) {
      myDefaultState.setLevel(level);
    }
    else {
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
      InspectionToolWrapper<?,?> toolWrapper = scopeToolState.getTool();
      NamedScope scope = scopeToolState.getScope(project);
      if (scope != null) {
        myTools.add(index, new ScopeToolState(scope, toolWrapper, scopeToolState.isEnabled(), level));
      }
      else {
        myTools.add(index, new ScopeToolState(scopeToolState.getScopeName(), toolWrapper, scopeToolState.isEnabled(), level));
      }
    }
  }

  public void setDefaultState(@NotNull InspectionToolWrapper<?,?> toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level) {
    myDefaultState.setTool(toolWrapper);
    myDefaultState.setLevel(level);
    myDefaultState.setEnabled(enabled);
  }

  public void setDefaultState(@NotNull InspectionToolWrapper<?,?> toolWrapper, boolean enabled, @NotNull HighlightDisplayLevel level, @Nullable String attributesKey) {
    setDefaultState(toolWrapper, enabled, level);
    myDefaultState.setEditorAttributesExternalName(attributesKey);
  }

  public void setLevel(@NotNull HighlightDisplayLevel level) {
    myDefaultState.setLevel(level);
  }

  @Nullable
  public List<ScopeToolState> getNonDefaultTools() {
    return myTools;
  }

  private void disableWholeToolIfCan() {
    if (myDefaultState.isEnabled()) {
      return;
    }
    if (myTools != null) {
      for (ScopeToolState tool : myTools) {
        if (tool.isEnabled()) {
          return;
        }
      }
    }
    setEnabled(false);
  }
}