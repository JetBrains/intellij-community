/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public final class ClassCouplingInspection
  extends ClassMetricInspection {

  private static final int DEFAULT_COUPLING_LIMIT = 15;
  /**
   * @noinspection PublicField
   */
  public boolean m_includeJavaClasses = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_includeLibraryClasses = false;

  @Override
  @NotNull
  public String getID() {
    return "OverlyCoupledClass";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer totalDependencies = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "overly.coupled.class.problem.descriptor", totalDependencies);
  }

  @Override
  protected int getDefaultLimit() {
    return DEFAULT_COUPLING_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "overly.coupled.class.class.coupling.limit.option");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", getConfigurationLabel(), 1, 1000),
      checkbox("m_includeJavaClasses", InspectionGadgetsBundle.message("include.java.system.classes.option")),
      checkbox("m_includeLibraryClasses", InspectionGadgetsBundle.message("include.library.classes.option"))
    );
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassCouplingVisitor();
  }

  private class ClassCouplingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // note: no call to super
      final int totalDependencies = calculateTotalDependencies(aClass);
      if (totalDependencies <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(totalDependencies));
    }

    private int calculateTotalDependencies(PsiClass aClass) {
      final CouplingVisitor visitor = new CouplingVisitor(aClass,
                                                          m_includeJavaClasses, m_includeLibraryClasses);
      aClass.accept(visitor);
      return visitor.getNumDependencies();
    }
  }
}