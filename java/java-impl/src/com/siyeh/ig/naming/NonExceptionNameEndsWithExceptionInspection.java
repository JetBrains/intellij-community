/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class NonExceptionNameEndsWithExceptionInspection extends BaseInspection {

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final String name = (String)infos[0];
    final Boolean onTheFly = (Boolean)infos[1];
    if (onTheFly.booleanValue()) {
      return new LocalQuickFix[]{new RenameFix(),
        new ExtendExceptionFix(name)};
    }
    else {
      return new LocalQuickFix[]{
        new ExtendExceptionFix(name)};
    }
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.exception.name.ends.with.exception.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonExceptionNameEndsWithExceptionVisitor();
  }

  private static class ExtendExceptionFix extends PsiUpdateModCommandQuickFix {

    private final String name;

    ExtendExceptionFix(String name) {
      this.name = name;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "non.exception.name.ends.with.exception.quickfix", name);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("extend.exception.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass aClass)) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      final PsiElementFactory factory = facade.getElementFactory();
      final GlobalSearchScope scope = aClass.getResolveScope();
      final PsiJavaCodeReferenceElement reference =
        factory.createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, scope);
      CommentTracker tracker = new CommentTracker();
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        tracker.delete(referenceElement);
      }
      tracker.insertCommentsBefore(extendsList.add(reference));
    }
  }

  private static class NonExceptionNameEndsWithExceptionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so it doesn't drill down into inner classes
      final String className = aClass.getName();
      if (className == null) {
        return;
      }
      @NonNls final String exception = "Exception";
      if (!className.endsWith(exception)) {
        return;
      }
      if (InheritanceUtil.isInheritor(aClass,
                                      CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      registerClassError(aClass, className,
                         Boolean.valueOf(isOnTheFly()));
    }
  }
}