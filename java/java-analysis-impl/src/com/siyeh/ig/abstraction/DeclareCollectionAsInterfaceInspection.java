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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class DeclareCollectionAsInterfaceInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreLocalVariables = false;
  /**
   * @noinspection PublicField
   */
  public boolean ignorePrivateMethodsAndFields = false;

  @Override
  @NotNull
  public String getID() {
    return "CollectionDeclaredAsConcreteClass";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    return InspectionGadgetsBundle.message(
      "collection.declared.by.class.problem.descriptor",
      type);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreLocalVariables", InspectionGadgetsBundle.message(
        "collection.declared.by.class.ignore.locals.option")),
      checkbox("ignorePrivateMethodsAndFields", InspectionGadgetsBundle.message(
        "collection.declared.by.class.ignore.private.members.option")));
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new DeclareCollectionAsInterfaceFix((String)infos[0]);
  }

  private static class DeclareCollectionAsInterfaceFix extends PsiUpdateModCommandQuickFix {

    private final String typeString;

    DeclareCollectionAsInterfaceFix(@NotNull String typeString) {
      this.typeString = typeString;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "declare.collection.as.interface.quickfix", typeString);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("declare.collection.as.interface.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement referenceElement)) {
        return;
      }
      final StringBuilder newElementText = new StringBuilder(typeString);
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null) {
        newElementText.append(parameterList.getText());
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiTypeElement)) {
        return;
      }
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(new CommentTracker().replaceAndRestoreComments(grandParent, newElementText.toString()));
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DeclareCollectionAsInterfaceVisitor();
  }

  private class DeclareCollectionAsInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      if (isOnTheFly() && DeclarationSearchUtils.isTooExpensiveToSearch(variable, false)) {
        registerPossibleProblem(variable.getNameIdentifier());
        return;
      }
      if (ignoreLocalVariables && variable instanceof PsiLocalVariable) {
        return;
      }
      if (ignorePrivateMethodsAndFields) {
        if (variable instanceof PsiField) {
          if (variable.hasModifierProperty(PsiModifier.PRIVATE)) {
            return;
          }
        }
      }
      if (variable instanceof PsiParameter parameter) {
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod method) {
          if (ignorePrivateMethodsAndFields) {
            if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
              return;
            }
          }
        }
        else if (ignoreLocalVariables) {
          return;
        }
      }
      final PsiType type = variable.getType();
      if (!CollectionUtils.isConcreteCollectionClass(type) || LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
        return;
      }

      checkToWeaken(type, variable.getTypeElement(), variable);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (ignorePrivateMethodsAndFields &&
          method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (isOnTheFly() && DeclarationSearchUtils.isTooExpensiveToSearch(method, false)) {
        registerPossibleProblem(method.getNameIdentifier());
        return;
      }
      final PsiType type = method.getReturnType();
      if (!CollectionUtils.isConcreteCollectionClass(type) || LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }

      checkToWeaken(type, method.getReturnTypeElement(), method);
    }

    private void checkToWeaken(PsiType type, PsiTypeElement typeElement, PsiElement variable) {
      if (typeElement == null) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = typeElement.getInnermostComponentReferenceElement();
      if (reference == null) {
        return;
      }
      final PsiElement nameElement = reference.getReferenceNameElement();
      if (nameElement == null) {
        return;
      }
      final Collection<PsiClass> weaklings = WeakestTypeFinder.calculateWeakestClassesNecessary(variable, false);
      if (weaklings.isEmpty()) {
        return;
      }
      final PsiClassType javaLangObject = PsiType.getJavaLangObject(nameElement.getManager(), nameElement.getResolveScope());
      final List<PsiClass> weaklingList = new ArrayList<>(weaklings);
      final PsiClass objectClass = javaLangObject.resolve();
      weaklingList.remove(objectClass);
      String qualifiedName = weaklingList.isEmpty() ? CollectionUtils.getInterfaceForClass(type.getCanonicalText())
                                                    : weaklingList.get(0).getQualifiedName();
      if (qualifiedName != null) {
        registerError(nameElement, qualifiedName);
      }
    }
  }
}