/*
 * User: anna
 * Date: 15-Apr-2009
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.profile.ProfileManager;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Tools {
  private static final Logger LOG = Logger.getInstance("#" + Tools.class.getName());

  private boolean myEnabled;
  private HighlightDisplayLevel myLevel;
  private String myShortName;
  private InspectionTool myTool;
  private List<ScopeToolState> myTools;

  public Tools(@NotNull InspectionTool tool, HighlightDisplayLevel level, boolean enabled) {
    myLevel = level;
    myEnabled = enabled;
    myShortName = tool.getShortName();
    myTool = tool;
  }



  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setLevel(HighlightDisplayLevel level) {
    myLevel = level;
  }

  public void addTool(NamedScope scope, InspectionTool tool, boolean enabled, HighlightDisplayLevel level) {
    if (myTools == null) {
      myTools = new ArrayList<ScopeToolState>();
      myTool = null;
    }
    myTools.add(new ScopeToolState(scope, tool, enabled, level));
  }

  public InspectionTool getInspectionTool(PsiElement element) {
    if (getTools() != null) {
      for (ScopeToolState state : getTools()) {
        if (element == null) {
          return (InspectionTool)state.getTool();
        }
        else {
          final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(element.getProject());
          if (state.getScope() != null && state.getScope().getValue().contains(element.getContainingFile(), validationManager)) {
            return (InspectionTool)state.getTool();
          }
        }
      }

      for (ScopeToolState state : getTools()) {
        if (state.getScope() == null) {
          return (InspectionTool)state.getTool();
        }
      }
    }
    return getTool();
  }

  public String getShortName() {
    return myShortName;
  }

  public List<InspectionTool> getAllTools() {
    if (getTool() != null) return Collections.singletonList(getTool());
    final List<InspectionTool> result = new ArrayList<InspectionTool>();
    for (ScopeToolState state : getTools()) {
      result.add((InspectionTool)state.getTool());
    }
    return result;
  }

  public InspectionTool getInspectionTool(NamedScope scope) {
    if (getTools() != null) {
      for (ScopeToolState state : getTools()) {
        if (scope == null || scope.equals(state.getScope())) return (InspectionTool)state.getTool();
      }
    }
    return getTool();
  }

  public void writeExternal(Element inspectionElement) throws WriteExternalException {
    inspectionElement.setAttribute(InspectionProfileImpl.LEVEL_TAG, getLevel().toString());
    inspectionElement.setAttribute(InspectionProfileImpl.ENABLED_TAG, Boolean.toString(isEnabled()));
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        final NamedScope namedScope = state.getScope();

        final Element scopeElement = new Element("scope");
        scopeElement.setAttribute("name", namedScope.getName());
        scopeElement.setAttribute(InspectionProfileImpl.LEVEL_TAG, state.getLevel().toString());
        scopeElement.setAttribute(InspectionProfileImpl.ENABLED_TAG, Boolean.toString(state.isEnabled()));
        InspectionProfileEntry inspectionTool = state.getTool();
        LOG.assertTrue(inspectionTool != null);
        inspectionTool.writeSettings(scopeElement);
        inspectionElement.addContent(scopeElement);
      }
    }
    else {
      myTool.writeSettings(inspectionElement);
    }
  }

  public void readExternal(Element toolElement, InspectionProfileImpl profile) throws InvalidDataException {
    final String levelName = toolElement.getAttributeValue(InspectionProfileImpl.LEVEL_TAG);
    final ProfileManager profileManager = profile.getProfileManager();
    HighlightDisplayLevel level =
      HighlightDisplayLevel.find(((SeverityProvider)profileManager).getOwnSeverityRegistrar().getSeverity(levelName));
    if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
      level = HighlightDisplayLevel.WARNING;
    }
    setLevel(level);

    final String enabled = toolElement.getAttributeValue(InspectionProfileImpl.ENABLED_TAG);
    setEnabled(enabled != null && Boolean.parseBoolean(enabled));

    final InspectionTool tool = getInspectionTool((NamedScope)null);
    final List children = toolElement.getChildren(InspectionProfileImpl.SCOPE);
    if (!children.isEmpty()) {
      for (Object sO : children) {
        final Element scopeElement = (Element)sO;
        final String scopeName = scopeElement.getAttributeValue(InspectionProfileImpl.NAME);
        if (scopeName != null) {
          final NamedScope namedScope = profileManager.getScopesManager().getScope(scopeName);
          if (namedScope != null) {
            final String errorLevel = scopeElement.getAttributeValue(InspectionProfileImpl.LEVEL_TAG);
            final String enabledInScope = scopeElement.getAttributeValue(InspectionProfileImpl.ENABLED_TAG);
            final InspectionTool inspectionTool = profile.myRegistrar.createInspectionTool(myShortName, tool);
            inspectionTool.readSettings(scopeElement);
            addTool(namedScope, inspectionTool, enabledInScope != null && Boolean.parseBoolean(enabledInScope),
                    errorLevel != null ? HighlightDisplayLevel
                      .find(((SeverityProvider)profileManager).getOwnSeverityRegistrar().getSeverity(errorLevel)) : level);
          }
        }
      }
    }
    else {
      tool.readSettings(toolElement);
    }
  }

  public InspectionTool getTool() {
    if (myTool == null) return (InspectionTool)myTools.iterator().next().getTool();
    return myTool;
  }

  public List<ScopeToolState> getTools() {
    if (myTools == null) return Collections.singletonList(new ScopeToolState(null, myTool, myEnabled, myLevel));
    return myTools;
  }

  public void setTool(InspectionTool tool, boolean enabled, HighlightDisplayLevel level) {
    myTool = tool;
    myEnabled = enabled;
    myLevel = level;
  }

  public List<NamedScope> getScopes() {
    final List<NamedScope> result = new ArrayList<NamedScope>();
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        result.add(state.getScope());
      }
    }
    else {
      result.add(null);
    }
    return result;
  }

  public void removeScope(NamedScope scope) {
    if (myTools != null) {
      //todo last
      for (Iterator<ScopeToolState> it = myTools.iterator(); it.hasNext();) {
        ScopeToolState pair = it.next();
        if (Comparing.equal(pair.getScope(), scope)) {
          it.remove();
          break;
        }
      }
    }
  }

  public void setScope(int idx, NamedScope namedScope) {
    if (myTools != null && myTools.size() > idx && idx >= 0) {
      final ScopeToolState scopeToolState = myTools.get(idx);
      final InspectionProfileEntry tool = scopeToolState.getTool();
      myTools.remove(idx);
      myTools.add(idx, new ScopeToolState(namedScope, tool, scopeToolState.isEnabled(), scopeToolState.getLevel()));
    }
  }

  public void moveScope(int idx, int dir) {
    if (myTools != null && idx >= 0 && idx < myTools.size() && idx + dir >= 0 && idx + dir < myTools.size()) {
      final ScopeToolState state = myTools.remove(idx);
      if (dir > 0) {

      }
    }
  }

  public boolean isEnabled(NamedScope namedScope) {
    if (myEnabled && myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(namedScope, state.getScope())) return state.isEnabled();
      }
    }
    return myEnabled;
  }

  public HighlightDisplayLevel getLevel(PsiElement element) {
    if (myTools == null || element == null) return myLevel;
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(element.getProject());
    for (ScopeToolState state : myTools) {
      final PackageSet set = state.getScope().getValue();
      if (set != null && set.contains(element.getContainingFile(), manager)) {
        return state.getLevel();
      }
    }
    return myLevel;
  }



  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public boolean isEnabled() {
    return myEnabled;
  }


  public boolean isEnabled(PsiElement element) {
    if (myTools == null || element == null) return myEnabled;
    final DependencyValidationManager manager = DependencyValidationManager.getInstance(element.getProject());
    for (ScopeToolState state : myTools) {
      final PackageSet set = state.getScope().getValue();
      if (set != null && set.contains(element.getContainingFile(), manager)) {
        return state.isEnabled();
      }
    }
    return myEnabled;
  }

  public void enableTool() {
    myEnabled = true;
  }

  public void enableTool(NamedScope namedScope) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(), namedScope)) {
          state.setEnabled(true);
        }
      }
    }
  }

  public void enableTool(PsiElement element) {

  }


  public void disableTool(NamedScope namedScope) {
    if (myTools != null) {
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(), namedScope)) {
          state.setEnabled(false);
        }
      }
    }
  }

  public void disableTool() {
    myEnabled = false;
  }


  public void disableTool(PsiElement element) {

  }

  public HighlightDisplayLevel getLevel(final NamedScope scope) {
    if (myTools != null){
      for (ScopeToolState state : myTools) {
        if (Comparing.equal(state.getScope(), scope)) {
          return state.getLevel();
        }
      }
    }
    return myLevel;
  }

   public boolean equalTo(Tools tools) {
      if (getTools() == null && tools.getTools() != null) return false;
      if (tools.getTools() == null && getTools() != null) return false;
      if (getTools() == null && tools.getTools() == null) {
        return toolSettingsAreEqual(myTool, tools.getTool());
      }
      if (getTools().size() != tools.getTools().size()) return false;
      for (int i = 0; i < getTools().size(); i++) {
        final ScopeToolState state = getTools().get(i);
        final ScopeToolState toolState = tools.getTools().get(i);
        if (!toolSettingsAreEqual(state.getTool(), toolState.getTool())){
          return false;
        }
        if (state.isEnabled() != toolState.isEnabled()) return false;
        if (state.getLevel() != toolState.getLevel()) return false;
      }
      return true;

  }

  private static boolean toolSettingsAreEqual(InspectionProfileEntry tool1, InspectionProfileEntry tool2) {
    try {
      @NonNls String tempRoot = "root";
      Element oldToolSettings = new Element(tempRoot);
      tool1.writeSettings(oldToolSettings);
      Element newToolSettings = new Element(tempRoot);
      tool2.writeSettings(newToolSettings);
      return JDOMUtil.areElementsEqual(oldToolSettings, newToolSettings);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return false;
  }
}