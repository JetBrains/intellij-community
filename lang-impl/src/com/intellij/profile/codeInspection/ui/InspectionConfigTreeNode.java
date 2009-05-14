/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.Nullable;

public class InspectionConfigTreeNode extends CheckedTreeNode {
  private NamedScope myScope;
  private boolean myByDefault;
  private boolean myInspectionNode;
  public boolean isProperSetting;

  public InspectionConfigTreeNode(Object userObject, NamedScope scope, boolean enabled, boolean properSetting, boolean inspectionNode) {
    this(userObject, scope, false, enabled, properSetting, inspectionNode);
  }

  public InspectionConfigTreeNode(Object userObject, NamedScope scope, boolean byDefault, boolean enabled, boolean properSetting, boolean inspectionNode) {
    super(userObject);
    myScope = scope;
    myByDefault = byDefault;
    isProperSetting = properSetting;
    myInspectionNode = inspectionNode;
    setChecked(enabled);
  }

  /*public boolean equals(Object obj) {
    if (!(obj instanceof MyTreeNode)) return false;
    MyTreeNode node = (MyTreeNode)obj;
    return isChecked() == node.isChecked() &&
           isByDefault() == node.isByDefault() &&
           isInspectionNode() == node.isInspectionNode() &&
           (getUserObject() != null ? node.getUserObject().equals(getUserObject()) : node.getUserObject() == null);
  }

  public int hashCode() {
    return getUserObject() != null ? getUserObject().hashCode() : 0;
  }*/

  @Nullable
  public Descriptor getDesriptor() {
    if (userObject instanceof String) return null;
    return (Descriptor)userObject;
  }

  public void setScope(NamedScope scope) {
    myScope = scope;
  }

  public NamedScope getScope() {
    return myScope;
  }

  public boolean isByDefault() {
    return myByDefault;
  }

  public String getGroupName() {
    return userObject instanceof String ? (String)userObject : null;
  }

  public boolean isInspectionNode() {
    return myInspectionNode;
  }

  public void setInspectionNode(boolean inspectionNode) {
    myInspectionNode = inspectionNode;
  }

  public void setByDefault(boolean byDefault) {
    myByDefault = byDefault;
  }
}