/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 16, 2001
 * Time: 12:50:45 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

public class RefProjectImpl extends RefEntityImpl implements RefProject {
  public RefProjectImpl(RefManager refManager) {
    super(refManager.getProject().getName(), refManager);
  }

  public boolean isValid() {
    return true;
  }
}
