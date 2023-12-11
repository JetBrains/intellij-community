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
package com.siyeh.ig.memory;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class InnerClassMayBeStaticInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public OrderedSet<String> ignorableAnnotations =
    new OrderedSet<>(Collections.singletonList(JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_NESTED));

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inner.class.may.be.static.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                 new JavaClassValidator().annotationsOnly()));
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> fixes = new ArrayList<>();
    fixes.add(new InnerClassMayBeStaticFix());
    final PsiClass aClass = (PsiClass)infos[0];
    fixes.addAll(SpecialAnnotationsUtilBase.createAddAnnotationToListFixes(aClass, this, insp -> insp.ignorableAnnotations));
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class InnerClassMayBeStaticFix extends ModCommandBatchQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("make.static.quickfix");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull List<ProblemDescriptor> descriptors) {
      final List<Handler> handlers = StreamEx.of(descriptors).map(descriptor -> descriptor.getPsiElement().getParent())
        .select(PsiClass.class).map(Handler::new).toList();
      return ModCommand.psiUpdate(ActionContext.from(descriptors.get(0)), updater -> {
        ContainerUtil.map(handlers, h -> h.getWritable(updater))
          .forEach(Handler::makeStatic);
      });
    }

    private static class Handler {
      private final @NotNull PsiClass innerClass;
      private final @NotNull List<@NotNull PsiElement> references;

      Handler(@NotNull PsiClass innerClass) {
        this.innerClass = innerClass;
        final Collection<PsiReference> references = ReferencesSearch.search(innerClass, innerClass.getUseScope()).findAll();
        this.references = ContainerUtil.map(references, PsiReference::getElement);
      }

      private Handler(@NotNull PsiClass innerClass, @NotNull List<@NotNull PsiElement> references) {
        this.innerClass = innerClass;
        this.references = references;
      }

      void makeStatic() {
        final PsiModifierList modifiers = innerClass.getModifierList();
        if (modifiers == null) {
          return;
        }
        modifiers.setModifierProperty(PsiModifier.STATIC, true);
        final Project project = innerClass.getProject();
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        references.stream()
          .sorted((r1, r2) -> PsiUtilCore.compareElementsByPosition(r2, r1))
          .forEach(reference -> {
            final PsiElement parent = reference.getParent();
            if (parent instanceof PsiNewExpression newExpression) {
              final PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
              if (classReference == null) {
                return;
              }
              final PsiExpressionList argumentList = newExpression.getArgumentList();
              if (argumentList == null) {
                return;
              }
              final PsiReferenceParameterList parameterList = classReference.getParameterList();
              final String genericParameters = parameterList != null ? parameterList.getText() : "";
              final String text = "new " + classReference.getQualifiedName() + genericParameters + argumentList.getText();
              final PsiExpression expression = factory.createExpressionFromText(text, innerClass);
              codeStyleManager.shortenClassReferences(newExpression.replace(expression));
            }
            else if (reference instanceof PsiJavaCodeReferenceElement ref) {
              removeTypeArguments(ref);
            }
          });
      }

      Handler getWritable(@NotNull ModPsiUpdater updater) {
        return new Handler(updater.getWritable(innerClass),
                           ContainerUtil.map(references, updater::getWritable));
      }

      private static void removeTypeArguments(PsiJavaCodeReferenceElement ref) {
        if (ref == null || !(ref.getQualifier() instanceof PsiJavaCodeReferenceElement qualifier)) {
          return;
        }
        removeTypeArguments(qualifier);
        PsiReferenceParameterList parameterList = qualifier.getParameterList();
        if (parameterList != null && parameterList.getFirstChild() != null) {
          parameterList.delete();
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InnerClassMayBeStaticVisitor();
  }

  private class InnerClassMayBeStaticVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.getContainingClass() != null && !aClass.hasModifierProperty(PsiModifier.STATIC) ||
          PsiUtil.isLocalOrAnonymousClass(aClass)) {
        if (!HighlightingFeature.INNER_STATICS.isAvailable(aClass)) {
          return;
        }
      }
      for (PsiClass innerClass : aClass.getInnerClasses()) {
        if (innerClass.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        if (AnnotationUtil.isAnnotated(innerClass, ignorableAnnotations, 0)) {
          continue;
        }
        final InnerClassReferenceVisitor visitor = new InnerClassReferenceVisitor(innerClass);
        innerClass.accept(visitor);
        if (!visitor.canInnerClassBeStatic()) {
          continue;
        }
        registerClassError(innerClass, innerClass);
      }
    }
  }
}