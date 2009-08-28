/*
 * User: anna
 * Date: 22-Jan-2008
 */
package com.intellij.ide.scopeView;

import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.event.TreeWillExpandListener;

public interface ScopeTreeStructureExpander extends TreeWillExpandListener {
  ExtensionPointName<ScopeTreeStructureExpander> EP_NAME = ExtensionPointName.create("com.intellij.scopeTreeExpander");
}