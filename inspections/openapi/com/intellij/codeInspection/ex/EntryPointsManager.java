/*
 * User: anna
  * Date: 28-Feb-2007
  */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.SmartRefElementPointer;

public interface EntryPointsManager {
  void resolveEntryPoints(RefManager manager);

  void addEntryPoint(RefElement newEntryPoint, boolean isPersistent);

  void removeEntryPoint(RefElement anEntryPoint);

  SmartRefElementPointer[] getEntryPoints();

  void cleanup();

  boolean isAddNonJavaEntries();
}