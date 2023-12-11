/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

public final class SystemRunFinalizersOnExitInspection extends BaseInspection {
  @Override
  @NotNull
  public String getID() {
    return "CallToSystemRunFinalizersOnExit";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "system.run.finalizers.on.exit.problem.descriptor");
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    // The method was removed in JDK 11
    return PsiUtil.getLanguageLevel(file).isLessThan(LanguageLevel.JDK_11);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SystemRunFinalizersOnExitVisitor();
  }

  private static class SystemRunFinalizersOnExitVisitor extends BaseInspectionVisitor {
    private static final CallMatcher RUN_FINALIZERS_ON_EXIT = CallMatcher.anyOf(
      CallMatcher.staticCall("java.lang.System", "runFinalizersOnExit"),
      CallMatcher.staticCall("java.lang.Runtime", "runFinalizersOnExit"));

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if (RUN_FINALIZERS_ON_EXIT.test(call)) {
        registerMethodCallError(call);
      }
    }
  }
}