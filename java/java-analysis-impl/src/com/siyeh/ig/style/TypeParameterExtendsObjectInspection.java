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
package com.siyeh.ig.style;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class TypeParameterExtendsObjectInspection extends BaseInspection {

  public boolean ignoreAnnotatedObject = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAnnotatedObject", InspectionGadgetsBundle.message("type.parameter.extends.object.ignore.annotated")));
  }

  @Override
  public @NotNull String getID() {
    return "TypeParameterExplicitlyExtendsObject";
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final Integer type = (Integer)infos[0];
    if (type.intValue() == 1) {
      return InspectionGadgetsBundle.message(
        "type.parameter.extends.object.problem.descriptor1");
    }
    else {
      return InspectionGadgetsBundle.message(
        "type.parameter.extends.object.problem.descriptor2");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new ExtendsObjectFix();
  }

  private static class ExtendsObjectFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "extends.object.remove.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement identifier, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = identifier.getParent();
      if (parent instanceof PsiTypeParameter typeParameter) {
        final PsiReferenceList extendsList =
          typeParameter.getExtendsList();
        final PsiJavaCodeReferenceElement[] referenceElements =
          extendsList.getReferenceElements();
        for (PsiJavaCodeReferenceElement referenceElement :
          referenceElements) {
          referenceElement.delete();
        }
      }
      else {
        final PsiTypeElement typeElement = (PsiTypeElement)parent;
        PsiElement child = typeElement.getLastChild();
        while (child != null) {
          if (child instanceof PsiJavaToken javaToken) {
            final IElementType tokenType = javaToken.getTokenType();
            if (tokenType == JavaTokenType.QUEST) {
              return;
            }
          }
          child.delete();
          child = typeElement.getLastChild();
        }
      }
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new ExtendsObjectVisitor();
  }

  private class ExtendsObjectVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeParameter(@NotNull PsiTypeParameter parameter) {
      super.visitTypeParameter(parameter);
      final PsiClassType[] extendsListTypes = parameter.getExtendsListTypes();
      if (extendsListTypes.length != 1) {
        return;
      }
      final PsiClassType extendsType = extendsListTypes[0];
      if (!extendsType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return;
      }
      if (ignoreAnnotatedObject && extendsType.hasAnnotations()) {
        return;
      }
      final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
      if (nameIdentifier == null) {
        return;
      }
      registerError(nameIdentifier, Integer.valueOf(1));
    }

    @Override
    public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
      super.visitTypeElement(typeElement);
      final PsiElement lastChild = typeElement.getLastChild();
      if (!(lastChild instanceof PsiTypeElement)) {
        return;
      }
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiWildcardType wildcardType)) {
        return;
      }
      if (!wildcardType.isExtends()) {
        return;
      }
      final PsiTypeElement extendsBound = (PsiTypeElement)typeElement.getLastChild();
      if ((ignoreAnnotatedObject && extendsBound.hasAnnotations()) || !TypeUtils.isJavaLangObject(extendsBound.getType())) {
        return;
      }
      if (ignoreAnnotatedObject && hasTypeContainerAnnotations(typeElement)) return;
      final PsiElement firstChild = typeElement.getFirstChild();
      if (firstChild == null) {
        return;
      }
      registerError(firstChild, Integer.valueOf(2));
    }

    /**
     * If there is a container {@code @NullMarked} annotation, then {@code <?>} means {@code <? extends @Nullable Object>}
     * but {@code <? extends Object>} mean {@code <? extends @NonNull Object>}.
     * So, in this case {@code extends Object} changes the behavior.
     * There is a case when we can delete it:
     * {@code <? extends @Nullable Object>}, but it seems it is better to preserve it because it makes code easier to understand
     * @return if there is a container annotation on the given element
     */
    private static boolean hasTypeContainerAnnotations(@Nullable PsiTypeElement typeElement) {
      if (typeElement == null) return false;
      NullableNotNullManager manager = NullableNotNullManager.getInstance(typeElement.getProject());
      if (manager != null) {
        NullabilityAnnotationInfo nullability = manager.findDefaultTypeUseNullability(typeElement);
        if (nullability != null && nullability.getNullability() != Nullability.UNKNOWN) {
          return true;
        }
      }
      return false;
    }
  }
}