/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.search.scope.packageSet.NamedScope;

import java.util.List;

public interface GlobalInspectionContextExtension<T> {
  Key<T> getID();

  void performPreRunActivities(final List<Pair<InspectionProfileEntry,NamedScope>> globalTools,
                               final List<Pair<InspectionProfileEntry, NamedScope>> localTools,
                               final GlobalInspectionContext context);

  void performPostRunActivities(final List<InspectionProfileEntry> inspections, final GlobalInspectionContext context);

  void cleanup();
}