/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class RedundantImplementsInspection extends BaseInspection implements CleanupLocalInspectionTool{

  @SuppressWarnings("PublicField")
  public boolean ignoreSerializable = false;
  @SuppressWarnings("PublicField")
  public boolean ignoreCloneable = false;

  @Override
  @NotNull
  public String getID() {
    return "RedundantInterfaceDeclaration";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.implements.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreSerializable", InspectionGadgetsBundle.message("ignore.serializable.option")),
      checkbox("ignoreCloneable", InspectionGadgetsBundle.message("ignore.cloneable.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new RedundantImplementsFix();
  }

  private static class RedundantImplementsFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.implements.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      startElement.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantImplementsVisitor();
  }

  private class RedundantImplementsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isAnnotationType()) {
        return;
      }
      if (aClass.isInterface()) {
        checkInterface(aClass);
      }
      else {
        checkConcreteClass(aClass);
      }
    }

    private void checkInterface(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsElements = extendsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement extendsElement : extendsElements) {
        checkExtendedInterface(extendsElement, extendsElements);
      }
    }

    private void checkConcreteClass(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (extendsList == null || implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsElements = extendsList.getReferenceElements();
      final PsiJavaCodeReferenceElement extendsElement;
      if (extendsElements.length != 1) {
        if (aClass.isEnum()) {
          final PsiElementFactory factory = JavaPsiFacade.getElementFactory(aClass.getProject());
          extendsElement = factory.createReferenceElementByFQClassName(CommonClassNames.JAVA_LANG_ENUM, aClass.getResolveScope());
        }
        else {
          extendsElement = null;
        }
      }
      else {
        extendsElement = extendsElements[0];
      }
      final PsiJavaCodeReferenceElement[] implementsElements = implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement implementsElement : implementsElements) {
        checkImplementedClass(implementsElement, extendsElement, implementsElements);
      }
    }

    private void checkImplementedClass(PsiJavaCodeReferenceElement implementsElement,
                                       PsiJavaCodeReferenceElement extendsElement,
                                       PsiJavaCodeReferenceElement[] implementsElements) {
      final PsiElement target = implementsElement.resolve();
      if (!(target instanceof PsiClass implementedClass)) {
        return;
      }
      if (!implementedClass.isInterface()) {
        return;
      }
      final String qualifiedName = implementedClass.getQualifiedName();
      if (ignoreSerializable && CommonClassNames.JAVA_IO_SERIALIZABLE.equals(qualifiedName)) {
        return;
      }
      else if (ignoreCloneable && CommonClassNames.JAVA_LANG_CLONEABLE.equals(qualifiedName)) {
        return;
      }
      if (extendsElement != null) {
        final PsiElement extendsReferent = extendsElement.resolve();
        if (!(extendsReferent instanceof PsiClass extendedClass)) {
          return;
        }
        if (extendedClass.isInheritor(implementedClass, true)) {
          registerError(implementsElement);
          return;
        }
      }
      check(implementsElement, implementedClass, implementsElements);
    }

    private void checkExtendedInterface(PsiJavaCodeReferenceElement extendsElement, PsiJavaCodeReferenceElement[] extendsElements) {
      final PsiElement target = extendsElement.resolve();
      if (!(target instanceof PsiClass extendedInterface) || !extendedInterface.isInterface()) {
        return;
      }
      check(extendsElement, extendedInterface, extendsElements);
    }

    private void check(PsiJavaCodeReferenceElement implementsElement,
                       PsiClass implementedClass,
                       PsiJavaCodeReferenceElement[] implementsElements) {
      for (PsiJavaCodeReferenceElement testImplementElement : implementsElements) {
        if (testImplementElement.equals(implementsElement) || !(testImplementElement.resolve() instanceof PsiClass testImplementedClass)) {
          continue;
        }
        if (testImplementedClass.isInheritor(implementedClass, true)) {
          registerError(implementsElement);
          return;
        }
      }
    }
  }
}