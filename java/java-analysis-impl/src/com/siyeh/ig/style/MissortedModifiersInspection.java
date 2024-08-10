/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.*;
import com.intellij.refactoring.util.ModifierListUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class MissortedModifiersInspection extends BaseInspection implements CleanupLocalInspectionTool{

  /**
   * @noinspection PublicField
   */
  public boolean m_requireAnnotationsFirst = true;

  public boolean typeUseWithType = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiModifierList modifierList = (PsiModifierList)infos[0];
    final List<String> modifiers = getModifiers(modifierList);
    final List<String> sortedModifiers = ModifierListUtil.getSortedModifiers(modifierList, null, !typeUseWithType);
    final List<String> missortedModifiers = stripCommonPrefixSuffix(modifiers, sortedModifiers);
    return InspectionGadgetsBundle.message("missorted.modifiers.problem.descriptor", String.join(" ", missortedModifiers));
  }

  private static <E> List<E> stripCommonPrefixSuffix(List<E> list1, List<E> list2) {
    final int max = list1.size() - commonSuffixLength(list1, list2);
    final List<E> result = new SmartList<>();
    for (int i = 0; i < max; i++) {
      final E token = list1.get(i);
      if (token.equals(list2.get(i))) continue; // common prefix
      result.add(token);
    }
    return result;
  }

  @Contract(pure = true)
  private static <E> int commonSuffixLength(@NotNull List<E> l1, @NotNull List<E> l2) {
    final int size1 = l1.size();
    final int size2 = l2.size();
    if (size1 == 0 || size2 == 0) return 0;
    int i = 0;
    for (; i < size1 && i < size2; i++) {
      if (!l1.get(size1 - i - 1).equals(l2.get(size2 - i - 1))) {
        break;
      }
    }
    return i;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MissortedModifiersVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new SortModifiersFix();
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "typeUseWithType");
    writeBooleanOption(node, "typeUseWithType", false);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_requireAnnotationsFirst", InspectionGadgetsBundle.message("missorted.modifiers.require.option"),
               checkbox("typeUseWithType", InspectionGadgetsBundle.message("missorted.modifiers.allowed.place"))
                 .description(HtmlChunk.raw(InspectionGadgetsBundle.message("missorted.modifiers.allowed.place.description",
                                                                            JavaBundle.message("generate.type.use.before.type")))))
        .description(HtmlChunk.raw(InspectionGadgetsBundle.message("missorted.modifiers.require.option.description")))
    );
  }

  private class SortModifiersFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("missorted.modifiers.sort.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiModifierList)) {
        element = element.getParent();
        if (!(element instanceof PsiModifierList)) return;
      }
      final PsiModifierList modifierList = (PsiModifierList)element;
      PsiModifierList newModifierList = ModifierListUtil.createSortedModifierList(modifierList, null, !typeUseWithType);
      if (newModifierList != null) {
        new CommentTracker().replaceAndRestoreComments(modifierList, newModifierList);
      }
    }
  }

  private static List<String> getModifiers(@NotNull PsiModifierList modifierList) {
    return Stream.of(modifierList.getChildren())
      .filter(e -> e instanceof PsiJavaToken || e instanceof PsiAnnotation)
      .map(PsiElement::getText)
      .collect(Collectors.toList());
  }

  private class MissortedModifiersVisitor extends BaseInspectionVisitor {

    private final Comparator<String> modifierComparator = new ModifierListUtil.ModifierComparator();

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      checkForMissortedModifiers(aClass);
    }

    @Override
    public void visitClassInitializer(
      @NotNull PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      checkForMissortedModifiers(initializer);
    }

    @Override
    public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      checkForMissortedModifiers(variable);
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      checkForMissortedModifiers(parameter);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      checkForMissortedModifiers(method);
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      checkForMissortedModifiers(field);
    }

    @Override
    public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
      super.visitRequiresStatement(statement);
      checkForMissortedModifiers(statement);
    }

    private void checkForMissortedModifiers(PsiModifierListOwner listOwner) {
      final PsiModifierList modifierList = listOwner.getModifierList();
      if (modifierList == null) {
        return;
      }
      final PsiElement modifier = getFirstMisorderedModifier(modifierList);
      if (modifier == null) {
        return;
      }
      registerError(isVisibleHighlight(modifierList) ? modifier : modifierList, modifierList);
    }

    private PsiElement getFirstMisorderedModifier(PsiModifierList modifierList) {
      if (modifierList == null) {
        return null;
      }
      final Deque<PsiElement> modifiers = new ArrayDeque<>();
      PsiAnnotation typeAnnotation = null;
      for (final PsiElement child : modifierList.getChildren()) {
        if (child instanceof PsiJavaToken) {
          if (typeAnnotation != null) return typeAnnotation;
          final String text = child.getText();
          if (!modifiers.isEmpty() && modifierComparator.compare(text, modifiers.getLast().getText()) < 0) {
            while (!modifiers.isEmpty()) {
              final PsiElement first = modifiers.pollFirst();
              if (modifierComparator.compare(text, first.getText()) < 0) {
                return first;
              }
            }
          }
          modifiers.add(child);
        }
        if (child instanceof PsiAnnotation annotation) {
          if (m_requireAnnotationsFirst) {
            if (AnnotationTargetUtil.isTypeAnnotation(annotation) && !ModifierListUtil.isMethodWithVoidReturnType(modifierList.getParent())) {
              // type annotations go next to the type
              // see e.g. https://www.oracle.com/technical-resources/articles/java/ma14-architect-annotations.html
              if (ModifierListUtil.isTypeAnnotationAlwaysUseWithType(annotation) || !modifiers.isEmpty()) {
                typeAnnotation = annotation;
              }
              final PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(annotation.getOwner());
              if (targets.length > 0 && AnnotationTargetUtil.findAnnotationTarget(annotation, targets[0]) == PsiAnnotation.TargetType.UNKNOWN) {
                typeAnnotation = annotation;
              }
              continue;
            }
            if (m_requireAnnotationsFirst && !modifiers.isEmpty()) {
              //things aren't in order, since annotations come first
              return modifiers.getFirst();
            }
          }
          else if (!modifiers.isEmpty()) {
            typeAnnotation = annotation;
          }
        }
      }
      return null;
    }
  }
}