/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 16, 2001
 * Time: 12:50:45 AM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.project.Project;

public class RefProjectImpl extends RefEntityImpl implements RefProject {
  private final RefManager myRefManager;
  private RefPackage myDefaultPackage;

  public RefProjectImpl(RefManager refManager) {
    super(getProjectFileName(refManager.getProject()));
    myRefManager = refManager;
  }

  private static String getProjectFileName(Project project) {
    return project.getName();
  }

  private RefManager getRefManager() {
    return myRefManager;
  }

  public RefPackage getDefaultPackage() {
    if (myDefaultPackage == null) {
      myDefaultPackage = getRefManager().getPackage(InspectionsBundle.message("inspection.reference.default.package"));
    }

    return myDefaultPackage;
  }
}
