/*
 * User: anna
 * Date: 20-Apr-2009
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.psi.search.scope.packageSet.NamedScope;

public class ScopeToolState {
  private NamedScope myScope;
  private InspectionProfileEntry myTool;
  private boolean myEnabled;
  private HighlightDisplayLevel myLevel;

  public ScopeToolState(NamedScope scope, InspectionProfileEntry tool, boolean enabled, HighlightDisplayLevel level) {
    myScope = scope;
    myTool = tool;
    myEnabled = enabled;
    myLevel = level;
  }

  public NamedScope getScope() {
    return myScope;
  }

  public InspectionProfileEntry getTool() {
    return myTool;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public HighlightDisplayLevel getLevel() {
    return myLevel;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public void setLevel(HighlightDisplayLevel level) {
    myLevel = level;
  }
}