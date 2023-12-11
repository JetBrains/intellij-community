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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddDefaultConstructorFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ChangeModifierFix;
import com.siyeh.ig.performance.ClassInitializerMayBeStaticInspection;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ClassInitializerInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnWhenConstructor = false;

  @Override
  @NotNull
  public String getID() {
    return "NonStaticInitializer";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.initializer.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("onlyWarnWhenConstructor", InspectionGadgetsBundle.message("class.initializer.option")));
  }


  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    PsiClassInitializer classInitializer = (PsiClassInitializer)infos[0];
    final PsiClass aClass = classInitializer.getContainingClass();
    assert aClass != null;
    if (PsiUtil.isInnerClass(aClass) && !HighlightingFeature.INNER_STATICS.isAvailable(aClass) || 
        ClassInitializerMayBeStaticInspection.dependsOnInstanceMembers(classInitializer)) {
      return new LocalQuickFix[] {new MoveToConstructorFix()};
    }
    return new LocalQuickFix[] {
      new ChangeModifierFix(PsiModifier.STATIC),
      new MoveToConstructorFix()
    };
  }

  private static class MoveToConstructorFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("class.initializer.move.code.to.constructor.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement brace, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = brace.getParent();
      if (!(parent instanceof PsiCodeBlock)) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiClassInitializer initializer)) {
        return;
      }
      final PsiClass aClass = initializer.getContainingClass();
      if (aClass == null) {
        return;
      }
      final Collection<PsiMethod> constructors = getOrCreateConstructors(aClass);
      for (PsiMethod constructor : constructors) {
        addCodeToMethod(initializer, constructor);
      }
      CommentTracker tracker = new CommentTracker();
      tracker.markUnchanged(initializer.getBody());
      tracker.deleteAndRestoreComments(initializer);
    }

    private static void addCodeToMethod(PsiClassInitializer initializer, PsiMethod constructor) {
      final PsiCodeBlock body = constructor.getBody();
      if (body == null) {
        return;
      }
      final PsiCodeBlock codeBlock = initializer.getBody();
      PsiElement element = codeBlock.getFirstBodyElement();
      final PsiElement last = codeBlock.getRBrace();
      while (element != null && element != last) {
        body.add(element);
        element = element.getNextSibling();
      }
    }

    @NotNull
    private static Collection<PsiMethod> getOrCreateConstructors(@NotNull PsiClass aClass) {
      PsiMethod[] constructors = aClass.getConstructors();
      if (constructors.length == 0) {
        AddDefaultConstructorFix.addDefaultConstructor(aClass);
      }
      constructors = aClass.getConstructors();
      return ContainerUtil.filter(constructors, constructor -> JavaHighlightUtil.getChainedConstructors(constructor).isEmpty());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassInitializerVisitor();
  }

  private class ClassInitializerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      if (initializer.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass aClass = initializer.getContainingClass();
      if (aClass == null || aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (onlyWarnWhenConstructor && aClass.getConstructors().length == 0) {
        return;
      }
      registerClassInitializerError(initializer, initializer);
    }
  }
}