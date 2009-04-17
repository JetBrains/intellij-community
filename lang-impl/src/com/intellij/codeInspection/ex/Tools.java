/*
 * User: anna
 * Date: 15-Apr-2009
 */
package com.intellij.codeInspection.ex;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.packageDependencies.DependencyValidationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Tools {
  private static final Logger LOG = Logger.getInstance("#" + Tools.class.getName());

  private String myShortName;
  private InspectionTool myTool;
  private List<Pair<NamedScope, InspectionTool>> myTools;

  public Tools(@NotNull InspectionTool tool) {
    myShortName = tool.getShortName();
    myTool = tool;
  }

  public void addTool(NamedScope scope, InspectionTool tool) {
    if (myTools == null) {
      myTools = new ArrayList<Pair<NamedScope, InspectionTool>>();
      myTool = null;
    }
    myTools.add(new Pair<NamedScope, InspectionTool>(scope, tool));
  }

  public InspectionTool getInspectionTool(PsiElement element) {
    if (getTools() != null) {
      for (Pair<NamedScope, InspectionTool> pair : getTools()) {
        if (element == null) {
          return pair.second;
        }
        else {
          final DependencyValidationManager validationManager = DependencyValidationManager.getInstance(element.getProject());
          if (pair.first != null && pair.first.getValue().contains(element.getContainingFile(), validationManager)) {
            return pair.second;
          }
        }
      }

      for (Pair<NamedScope, InspectionTool> pair : getTools()) {
        if (pair.first == null) {
          return pair.second;
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
    for (Pair<NamedScope, InspectionTool> pair : getTools()) {
      result.add(pair.second);
    }
    return result;
  }

  public InspectionTool getInspectionTool(NamedScope scope) {
    if (getTools() != null) {
      for (Pair<NamedScope, InspectionTool> pair : getTools()) {
        if (scope == null || scope.equals(pair.first)) return pair.second;
      }
    }
    return getTool();
  }

  public void writeExternal(Element inspectionElement, Map<String, Element> scopesElements) throws WriteExternalException {
    if (myTools != null) {
      for (Pair<NamedScope, InspectionTool> toolPair : myTools) {
        final NamedScope namedScope = toolPair.first;
        final Element scopeElement = scopesElements.get(namedScope.getName());
        LOG.assertTrue(scopeElement != null);
        InspectionTool inspectionTool = toolPair.second;
        LOG.assertTrue(inspectionTool != null);
        inspectionTool.writeSettings(scopeElement);
      }
    }
    else {
      myTool.writeSettings(inspectionElement);
    }
  }

  public InspectionTool getTool() {
    if (myTool == null) return myTools.iterator().next().second;
    return myTool;
  }

  public List<Pair<NamedScope, InspectionTool>> getTools() {
    if (myTools == null) return Collections.singletonList(new Pair<NamedScope, InspectionTool>(null, myTool));
    return myTools;
  }

  public void setTool(InspectionTool tool) {
    myTool = tool;
  }
}