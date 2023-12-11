// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ExceptionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;

public final class TooBroadThrowsInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean onlyWarnOnRootExceptions = false;

  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility

  @SuppressWarnings("PublicField")
  public boolean ignoreLibraryOverrides = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreThrown = false;
  @SuppressWarnings("PublicField")
  public int hiddenExceptionsThreshold = 10;

  @Override
  @NotNull
  public String getID() {
    return "OverlyBroadThrowsClause";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final List<SmartTypePointer> typesMasked = (List<SmartTypePointer>)infos[0];
    final PsiType type = typesMasked.get(0).getType();
    String typesMaskedString = type != null ? type.getPresentableText() : "";
    if (typesMasked.size() == 1) {
      return InspectionGadgetsBundle.message(
        "overly.broad.throws.clause.problem.descriptor1",
        typesMaskedString);
    }
    else {
      final int lastTypeIndex = typesMasked.size() - 1;
      for (int i = 1; i < lastTypeIndex; i++) {
        final PsiType psiType = typesMasked.get(i).getType();
        if (psiType != null) {
          typesMaskedString += ", ";
          typesMaskedString += psiType.getPresentableText();
        }
      }
      final PsiType psiType = typesMasked.get(lastTypeIndex).getType();
      final String lastTypeString = psiType != null ? psiType.getPresentableText() : "";
      return InspectionGadgetsBundle.message("overly.broad.throws.clause.problem.descriptor2", typesMaskedString, lastTypeString);
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("hiddenExceptionsThreshold", InspectionGadgetsBundle.message("overly.broad.throws.clause.threshold.option"),
             1, 100),
     checkbox("onlyWarnOnRootExceptions", InspectionGadgetsBundle.message("too.broad.catch.option")),
      checkbox("ignoreLibraryOverrides", InspectionGadgetsBundle.message("ignore.exceptions.declared.on.library.override.option")),
      checkbox("ignoreThrown", InspectionGadgetsBundle.message("overly.broad.throws.clause.ignore.thrown.option")));
  }

  @NotNull
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Collection<SmartTypePointer> maskedExceptions = (Collection<SmartTypePointer>)infos[0];
    final Boolean originalNeeded = (Boolean)infos[1];
    return new AddThrowsClauseFix(maskedExceptions, originalNeeded.booleanValue());
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[2];
    final SuppressForTestsScopeFix suppressFix = SuppressForTestsScopeFix.build(this, context);
    if (suppressFix == null) {
      return new LocalQuickFix[] {buildFix(infos)};
    }
    return new LocalQuickFix[] {buildFix(infos), suppressFix};
  }

  private static class AddThrowsClauseFix extends PsiUpdateModCommandQuickFix {

    private final Collection<? extends SmartTypePointer> types;
    private final boolean originalNeeded;

    AddThrowsClauseFix(Collection<? extends SmartTypePointer> types, boolean originalNeeded) {
      this.types = types;
      this.originalNeeded = originalNeeded;
    }

    @Override
    @NotNull
    public String getName() {
      if (originalNeeded) {
        return InspectionGadgetsBundle.message("overly.broad.throws.clause.quickfix1");
      }
      else {
        return InspectionGadgetsBundle.message("overly.broad.throws.clause.quickfix2");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("add.throws.clause.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiReferenceList referenceList)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (!originalNeeded) {
        element.delete();
      }
      for (SmartTypePointer type : types) {
        final PsiType psiType = type.getType();
        if (psiType instanceof PsiClassType) {
          final PsiJavaCodeReferenceElement referenceElement = factory.createReferenceElementByType((PsiClassType)psiType);
          referenceList.add(referenceElement);
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TooBroadThrowsVisitor();
  }

  private class TooBroadThrowsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiReferenceList throwsList = method.getThrowsList();
      if (!throwsList.isPhysical()) {
        return;
      }
      final PsiJavaCodeReferenceElement[] throwsReferences = throwsList.getReferenceElements();
      if (throwsReferences.length == 0) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      if (ignoreLibraryOverrides && LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }
      final Set<PsiClassType> exceptionsThrown = ExceptionUtils.calculateExceptionsThrown(body);
      final PsiClassType[] referencedExceptions = throwsList.getReferencedTypes();
      final Set<PsiType> exceptionsDeclared = new HashSet<>(referencedExceptions.length);
      ContainerUtil.addAll(exceptionsDeclared, referencedExceptions);
      final int referencedExceptionsLength = referencedExceptions.length;
      for (int i = 0; i < referencedExceptionsLength; i++) {
        final PsiClassType referencedException = referencedExceptions[i];
        if (onlyWarnOnRootExceptions) {
          if (!ExceptionUtils.isGenericExceptionClass(
            referencedException)) {
            continue;
          }
        }
        final List<SmartTypePointer> exceptionsMasked = new ArrayList<>();
        final SmartTypePointerManager pointerManager = SmartTypePointerManager.getInstance(body.getProject());
        for (PsiType exceptionThrown : exceptionsThrown) {
          if (referencedException.isAssignableFrom(exceptionThrown) && !exceptionsDeclared.contains(exceptionThrown)) {
            exceptionsMasked.add(pointerManager.createSmartTypePointer(exceptionThrown));
          }
        }
        if (!exceptionsMasked.isEmpty()) {
          int numberOfHiddenExceptions = exceptionsMasked.size();
          if (numberOfHiddenExceptions > hiddenExceptionsThreshold) {
            continue;
          }
          final PsiJavaCodeReferenceElement throwsReference = throwsReferences[i];
          final boolean originalNeeded = exceptionsThrown.contains(referencedException);
          if (ignoreThrown && originalNeeded) {
            continue;
          }
          registerError(throwsReference, exceptionsMasked, Boolean.valueOf(originalNeeded), throwsReference);
        }
      }
    }
  }
}