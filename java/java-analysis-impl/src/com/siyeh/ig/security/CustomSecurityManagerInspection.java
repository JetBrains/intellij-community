/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.security;

import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class CustomSecurityManagerInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "custom.security.manager.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CustomSecurityManagerVisitor();
  }

  private static class CustomSecurityManagerVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!InheritanceUtil.isInheritor(aClass,
                                       "java.lang.SecurityManager")) {
        return;
      }
      if ("java.lang.SecurityManager".equals(aClass.getQualifiedName())) {
        return;
      }
      registerClassError(aClass);
    }
  }
}