// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnreferencedFilter;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.unusedSymbol.VisibilityModifierChooser;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.VisibilityUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class UnusedReturnValue extends GlobalJavaBatchInspectionTool{
  public boolean IGNORE_BUILDER_PATTERN;
  @PsiModifier.ModifierConstant
  public static final String DEFAULT_HIGHEST_MODIFIER = PsiModifier.PUBLIC;
  @PsiModifier.ModifierConstant
  public String highestModifier = DEFAULT_HIGHEST_MODIFIER;

  @Override
  public CommonProblemDescriptor @Nullable [] checkElement(@NotNull RefEntity refEntity,
                                                           @NotNull AnalysisScope scope,
                                                           @NotNull InspectionManager manager,
                                                           @NotNull GlobalInspectionContext globalContext,
                                                           @NotNull ProblemDescriptionsProcessor processor) {
    if (refEntity instanceof RefMethod) {
      final RefMethod refMethod = (RefMethod)refEntity;

      if (VisibilityUtil.compare(refMethod.getAccessModifier(), highestModifier) < 0 ||
          refMethod.isConstructor() ||
          !refMethod.getSuperMethods().isEmpty() ||
          refMethod.getInReferences().isEmpty() ||
          refMethod.isEntry() ||
          refMethod.isReturnValueUsed() ||
          UnreferencedFilter.isExternallyReferenced(refMethod)) {
        return null;
      }

      final PsiMethod psiMethod = (PsiMethod)refMethod.getUastElement().getJavaPsi();
      if (psiMethod == null) return null;
      if (IGNORE_BUILDER_PATTERN && PropertyUtilBase.isSimplePropertySetter(psiMethod)) return null;

      final boolean isNative = psiMethod.hasModifierProperty(PsiModifier.NATIVE);
      if (refMethod.isExternalOverride() && !isNative) return null;
      if (RefUtil.isImplicitRead(psiMethod)) return null;
      if (MethodUtils.hasCanIgnoreReturnValueAnnotation(psiMethod, psiMethod.getContainingFile())) return null;
      return new ProblemDescriptor[]{createProblemDescriptor(psiMethod, manager, processor, isNative, false)};
    }
    return null;
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    if (IGNORE_BUILDER_PATTERN || highestModifier != DEFAULT_HIGHEST_MODIFIER) {
      super.writeSettings(node);
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(JavaBundle.message("checkbox.ignore.simple.setters"), "IGNORE_BUILDER_PATTERN");
    VisibilityModifierChooser modifierChooser = new VisibilityModifierChooser(() -> true,
                                                                              highestModifier,
                                                                              (newModifier) -> highestModifier = newModifier);
    panel.addComponent(LabeledComponent.create(modifierChooser, JavaBundle.message("label.maximal.reported.method.visibility"),
                                               BorderLayout.WEST));
    return panel;
  }

  @Override
  protected boolean queryExternalUsagesRequests(@NotNull final RefManager manager,
                                                @NotNull final GlobalJavaInspectionContext globalContext,
                                                @NotNull final ProblemDescriptionsProcessor processor) {
    manager.iterate(new RefJavaVisitor() {
      @Override public void visitElement(@NotNull RefEntity refEntity) {
        if (refEntity instanceof RefElement && processor.getDescriptions(refEntity) != null) {
          refEntity.accept(new RefJavaVisitor() {
            @Override public void visitMethod(@NotNull final RefMethod refMethod) {
              if (PsiModifier.PRIVATE.equals(refMethod.getAccessModifier())) return;
              globalContext.enqueueMethodUsagesProcessor(refMethod, new GlobalJavaInspectionContext.UsagesProcessor() {
                @Override
                public boolean process(PsiReference psiReference) {
                  processor.ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.declaration.redundancy");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "UnusedReturnValue";
  }

  @Override
  @Nullable
  public QuickFix getQuickFix(String hint) {
    return new MakeVoidQuickFix(null);
  }

  @Nullable
  @Override
  public LocalInspectionTool getSharedLocalInspectionTool() {
    return new UnusedReturnValueLocalInspection(this);
  }


  static ProblemDescriptor createProblemDescriptor(@NotNull PsiMethod psiMethod,
                                                   @NotNull InspectionManager manager,
                                                   @Nullable ProblemDescriptionsProcessor processor,
                                                   boolean isNative, boolean isOnTheFly) {
    return manager.createProblemDescriptor(psiMethod.getNameIdentifier(),
                                           JavaBundle.message("inspection.unused.return.value.problem.descriptor"),
                                           isNative ? null : new MakeVoidQuickFix(processor),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           isOnTheFly);
  }
}
