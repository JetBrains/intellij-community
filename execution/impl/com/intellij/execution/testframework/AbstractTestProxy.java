/*
 * User: anna
 * Date: 23-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public interface AbstractTestProxy {
  @NonNls String DATA_CONSTANT = "testProxy";

  boolean isInProgress();

  boolean isDefect();

  //todo?
  boolean shouldRun();

  int getMagnitude();

  boolean isLeaf();

  String getName();

  Location getLocation(final Project project);

  Navigatable getDescriptor(final Location location);

  AbstractTestProxy getParent();

  List<? extends AbstractTestProxy> getAllTests();
}