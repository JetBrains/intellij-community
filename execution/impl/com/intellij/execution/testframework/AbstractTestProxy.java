/*
 * User: anna
 * Date: 23-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;

public interface AbstractTestProxy {
  boolean isInProgress();
  boolean isDefect();
  boolean shouldRun();
  int getMagnitude();
  boolean isLeaf();
  AbstractTestProxy getParent();
  Location getLocation(final Project project);
  Navigatable getDescriptor(final Location location);
}