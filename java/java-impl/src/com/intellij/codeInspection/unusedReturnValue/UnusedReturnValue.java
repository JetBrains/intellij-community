/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.deadCode.UnreferencedFilter;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.unusedSymbol.VisibilityModifierChooser;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.VisibilityUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

/**
 * @author max
 */
public class UnusedReturnValue extends GlobalJavaBatchInspectionTool{
  public boolean IGNORE_BUILDER_PATTERN;
  @PsiModifier.ModifierConstant
  public static final String DEFAULT_HIGHEST_MODIFIER = PsiModifier.PUBLIC;
  @PsiModifier.ModifierConstant
  public String highestModifier = DEFAULT_HIGHEST_MODIFIER;

  @Override
  @Nullable
  public CommonProblemDescriptor[] checkElement(@NotNull RefEntity refEntity,
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
      if (canIgnoreReturnValue(psiMethod)) return null;
      return new ProblemDescriptor[]{createProblemDescriptor(psiMethod, manager, processor, isNative, false)};
    }
    return null;
  }

  static boolean canIgnoreReturnValue(PsiMethod psiMethod) {
    return AnnotationUtil.isAnnotated(psiMethod, 
                                      Collections.singleton("com.google.errorprone.annotations.CanIgnoreReturnValue"), 
                                      AnnotationUtil.CHECK_HIERARCHY);
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
    panel.addCheckbox("Ignore simple setters", "IGNORE_BUILDER_PATTERN");
    LabeledComponent<VisibilityModifierChooser> component = LabeledComponent.create(new VisibilityModifierChooser(() -> true,
                                                                                                                  highestModifier,
                                                                                                                  (newModifier) -> highestModifier = newModifier),
                                                                                    "Maximal reported method visibility:",
                                                                                    BorderLayout.WEST);
    panel.addComponent(component);
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
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.unused.return.value.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
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
                                           InspectionsBundle.message("inspection.unused.return.value.problem.descriptor"),
                                           isNative ? null : new MakeVoidQuickFix(processor),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           isOnTheFly);
  }
}
