// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.psi.PsiClass;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InheritanceUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public final class InterfaceNeverImplementedInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInterfacesThatOnlyDeclareConstants = false;

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignorableAnnotations");
    ignorableAnnotations.writeSettings(node, "ignorableAnnotations");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    return SpecialAnnotationsUtilBase.createAddAnnotationToListFixes(aClass, this, insp -> insp.ignorableAnnotations)
      .toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                 new JavaClassValidator().annotationsOnly()),
      checkbox("ignoreInterfacesThatOnlyDeclareConstants", InspectionGadgetsBundle.message("interface.never.implemented.option"))
    );
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "interface.never.implemented.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InterfaceNeverImplementedVisitor();
  }

  private class InterfaceNeverImplementedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!aClass.isInterface() || aClass.isAnnotationType()) {
        return;
      }
      if (ignoreInterfacesThatOnlyDeclareConstants && aClass.getMethods().length == 0 && aClass.getFields().length != 0) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0) || InheritanceUtil.hasImplementation(aClass)) {
        return;
      }
      registerClassError(aClass, aClass);
    }
  }
}