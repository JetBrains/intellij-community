/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.Key;

import java.util.List;

public interface GlobalInspectionContextExtension<T> {
  Key<T> getID();

  void performPostRunActivities(List<InspectionProfileEntry> inspections, GlobalInspectionContext context);

  void cleanup();
}