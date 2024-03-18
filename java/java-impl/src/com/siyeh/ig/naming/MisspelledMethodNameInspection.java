/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.CollectionFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class MisspelledMethodNameInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreIfMethodIsOverride = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreIfMethodIsOverride", InspectionGadgetsBundle.message("ignore.methods.overriding.super.method")));
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix((String)infos[0]);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.names.differ.only.by.case.problem.descriptor", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodNamesDifferOnlyByCaseVisitor();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private class MethodNamesDifferOnlyByCaseVisitor extends BaseInspectionVisitor {
    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      PsiMethod[] methods = aClass.getAllMethods();
      Map<String, PsiMethod> methodNames = CollectionFactory.createCaseInsensitiveStringMap();
      Map<PsiIdentifier, String> errorNames = new HashMap<>();
      for (PsiMethod method : methods) {
        ProgressManager.checkCanceled();
        if (method.isConstructor()) continue;
        if (ignoreIfMethodIsOverride && MethodUtils.hasSuper(method)) {
          continue;
        }

        String name = method.getName();
        PsiMethod existing = methodNames.get(name);
        if (existing == null) {
          methodNames.put(name, method);
        }
        else {
          PsiClass methodClass = method.getContainingClass();
          PsiClass existingMethodClass = existing.getContainingClass();
          String existingName = existing.getName();
          if (!name.equals(existingName)) {
            if (existingMethodClass == aClass) {
              PsiIdentifier identifier = existing.getNameIdentifier();
              if (identifier != null) {
                errorNames.put(identifier, name);
              }
            }
            if (methodClass == aClass) {
              PsiIdentifier identifier = method.getNameIdentifier();
              if (identifier != null && identifier.isPhysical()) {
                errorNames.put(identifier, existingName);
              }
            }
          }
        }
      }
      for (Map.Entry<PsiIdentifier, String> entry : errorNames.entrySet()) {
        PsiIdentifier identifier = entry.getKey();
        String otherName = entry.getValue();
        registerError(identifier, otherName);
      }
    }
  }
}