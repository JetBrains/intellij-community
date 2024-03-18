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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class MethodOverridesInaccessibleMethodOfSuperInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(infos[0].equals(PsiModifier.PACKAGE_LOCAL) ?
      "method.overrides.package.local.method.problem.descriptor" :
      "method.overrides.private.display.name.problem.descriptor"
    );
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }
  
  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOverridesPrivateMethodVisitor();
  }

  private static class MethodOverridesPrivateMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }

      if (JavaHighlightUtil.isSerializationRelatedMethod(method, aClass)) {
        return;
      }
      
      PsiClass ancestorClass = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          break;
        }
        final PsiSubstitutor classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(ancestorClass, aClass, PsiSubstitutor.EMPTY);
        final PsiMethod overridingMethod = MethodSignatureUtil.findMethodInSuperClassBySignatureInDerived(aClass, ancestorClass, 
                                                                                                          method.getSignature(classSubstitutor), false);
        if (overridingMethod != null) {
          if (overridingMethod.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
            final PsiJavaFile file = PsiTreeUtil.getParentOfType(aClass, PsiJavaFile.class);
            if (file == null) {
              break;
            }
            final PsiJavaFile ancestorFile = PsiTreeUtil.getParentOfType(ancestorClass, PsiJavaFile.class);
            if (ancestorFile == null) {
              break;
            }
            final String packageName = file.getPackageName();
            final String ancestorPackageName = ancestorFile.getPackageName();
            if (!packageName.equals(ancestorPackageName)) {
              registerMethodError(method, PsiModifier.PACKAGE_LOCAL);
              break;
            }
          }
          else if (overridingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
            registerMethodError(method, PsiModifier.PRIVATE);
            break;
          }
        }
        ancestorClass = ancestorClass.getSuperClass();
      }
    }
  }
}