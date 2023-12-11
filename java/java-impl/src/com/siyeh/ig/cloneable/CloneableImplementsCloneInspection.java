/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.cloneable;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RemoveCloneableFix;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class CloneableImplementsCloneInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreCloneableDueToInheritance = true;

  public boolean ignoreWhenCloneCalled = true;

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignoreWhenCloneCalled");
    writeBooleanOption(node, "ignoreWhenCloneCalled", true);
  }

  @Override
  @NotNull
  public String getID() {
    return "CloneableClassWithoutClone";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("cloneable.class.without.clone.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreCloneableDueToInheritance", InspectionGadgetsBundle.message("cloneable.class.without.clone.ignore.option")),
      checkbox("ignoreWhenCloneCalled", InspectionGadgetsBundle.message("cloneable.class.without.clone.ignore.when.clone.called.option")));
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> fixes = new SmartList<>();
    final PsiClass aClass = (PsiClass)infos[0];
    if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
      final PsiMethod[] superMethods = aClass.findMethodsByName(HardcodedMethodConstants.CLONE, true);
      boolean generateTryCatch = true;
      boolean createFix = true;
      for (PsiMethod method : superMethods) {
        if (CloneUtils.isClone(method)) {
          if (method.hasModifierProperty(PsiModifier.FINAL)) {
            createFix = false;
          }
          generateTryCatch &= MethodUtils.hasInThrows(method, "java.lang.CloneNotSupportedException");
        }
      }
      if (createFix) {
        fixes.add(new CreateCloneMethodFix(generateTryCatch));
      }
    }
    final boolean cloneCalled = (boolean)infos[1];
    if (CloneUtils.isDirectlyCloneable(aClass) && !cloneCalled) {
      fixes.add(RemoveCloneableFix.create(null));
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class CreateCloneMethodFix extends PsiUpdateModCommandQuickFix {

    private final boolean myGenerateTryCatch;

    CreateCloneMethodFix(boolean generateTryCatch) {
      myGenerateTryCatch = generateTryCatch;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("cloneable.class.without.clone.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass aClass)) {
        return;
      }
      @NonNls final StringBuilder methodText = new StringBuilder();
      final JavaCodeStyleSettings codeStyleSettings = JavaCodeStyleSettings.getInstance(aClass.getContainingFile());
      if (PsiUtil.isLanguageLevel5OrHigher(aClass) && codeStyleSettings.INSERT_OVERRIDE_ANNOTATION) {
        methodText.append("@java.lang.Override ");
      }
      final String className = aClass.getName();
      methodText.append("public ").append(className);
      final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
      if (typeParameterList != null) {
        methodText.append(typeParameterList.getText());
      }
      methodText.append(" clone() {\n");
      if (myGenerateTryCatch) {
        methodText.append("try {\n");
      }
      if (aClass.getFields().length > 0) {
        if (codeStyleSettings.GENERATE_FINAL_LOCALS) {
          methodText.append("final ");
        }
        methodText.append(className).append(" clone = (").append(className).append(") super.clone();\n");
        methodText.append("  // ").append(InspectionGadgetsBundle.message("cloneable.class.without.clone.todo.message"));
        methodText.append("\nreturn clone;\n");
      }
      else {
        methodText.append("return (").append(className).append(") super.clone();\n");
      }
      if (myGenerateTryCatch) {
        methodText.append("""
                            } catch (CloneNotSupportedException e) {
                            throw new AssertionError();
                            }
                            """);
      }
      methodText.append("}");
      final PsiMethod method = JavaPsiFacade.getElementFactory(project).createMethodFromText(methodText.toString(), element);
      final PsiElement newElement = parent.add(method);
      GenerateMembersUtil.positionCaret(updater, newElement, true);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneableImplementsCloneVisitor();
  }

  private class CloneableImplementsCloneVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass.isEnum()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (!CloneUtils.isCloneable(aClass)) {
        return;
      }
      for (PsiMethod method : aClass.findMethodsByName("clone", false)) {
        if (CloneUtils.isClone(method)) {
          return;
        }
      }
      final boolean directlyCloneable = CloneUtils.isDirectlyCloneable(aClass);
      final boolean cloneCalled = directlyCloneable && RemoveCloneableFix.isCloneCalledInChildren(aClass);
      final boolean highlight = (!m_ignoreCloneableDueToInheritance || directlyCloneable) &&
                                (!ignoreWhenCloneCalled || !cloneCalled);
      if (!highlight && !isOnTheFly()) {
        return;
      }
      final PsiElement elementToHighlight =
        aClass instanceof PsiAnonymousClass ? ((PsiAnonymousClass)aClass).getBaseClassReference() : aClass.getNameIdentifier();
      if (elementToHighlight != null) {
        registerError(elementToHighlight,
                      highlight ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.INFORMATION,
                      aClass,
                      cloneCalled);
      }
    }
  }
}