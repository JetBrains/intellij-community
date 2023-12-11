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
package com.siyeh.ig.security;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.RemoveCloneableFix;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class CloneableClassInSecureContextInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("cloneable.class.in.secure.context.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    // the quick fixes below probably require some thought and shouldn't be applied blindly on many classes at once
    return true;
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    if (CloneUtils.isDirectlyCloneable(aClass)) {
      final RemoveCloneableFix fix = RemoveCloneableFix.create(aClass);
      if (fix != null) {
        return fix;
      }
    }
    final boolean hasOwnCloneMethod = ContainerUtil.exists(aClass.findMethodsByName("clone", false), CloneUtils::isClone);
    if (hasOwnCloneMethod) {
      return null;
    }
    final boolean hasParentFinalCloneMethod = ContainerUtil.exists(aClass.findMethodsByName("clone", true),
                                                                   m -> CloneUtils.isClone(m) && m.hasModifierProperty(PsiModifier.FINAL));
    if (hasParentFinalCloneMethod) {
      return null;
    }
    return new CreateExceptionCloneMethodFix();
  }

  private static class CreateExceptionCloneMethodFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("cloneable.class.in.secure.context.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element.getParent() instanceof PsiClass aClass)) {
        return;
      }
      @NonNls final StringBuilder methodText = new StringBuilder();
      if (PsiUtil.isLanguageLevel5OrHigher(aClass) &&
          JavaCodeStyleSettings.getInstance(aClass.getContainingFile()).INSERT_OVERRIDE_ANNOTATION) {
        methodText.append("@java.lang.Override ");
      }
      methodText.append("protected ");
      final String name = aClass.getName();
      if (name != null) {
        methodText.append(name);
      }
      else if (aClass instanceof PsiAnonymousClass) {
        final PsiClassType baseClassType = ((PsiAnonymousClass)aClass).getBaseClassType();
        methodText.append(baseClassType.getCanonicalText());
      }
      else {
        methodText.append(CommonClassNames.JAVA_LANG_OBJECT);
      }
      final PsiTypeParameterList typeParameterList = aClass.getTypeParameterList();
      if (typeParameterList != null) {
        methodText.append(typeParameterList.getText());
      }
      methodText.append(" clone() {}");
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiMethod method = (PsiMethod)aClass.add(factory.createMethodFromText(methodText.toString(), aClass));
      final PsiClassType exceptionType = factory.createTypeByFQClassName("java.lang.CloneNotSupportedException", element.getResolveScope());
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      boolean throwException = false;
      if (superMethod != null) {
        if (superMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
          method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        }
        for (PsiClassType thrownType : superMethod.getThrowsList().getReferencedTypes()) {
          if (thrownType.equals(exceptionType)) {
            throwException = true;
            break;
          }
        }
        if (throwException) {
          final PsiJavaCodeReferenceElement exceptionReference = factory.createReferenceElementByType(exceptionType);
          method.getThrowsList().add(exceptionReference);
        }
        else {
          final PsiJavaCodeReferenceElement errorReference =
            factory.createFQClassNameReferenceElement("java.lang.AssertionError", element.getResolveScope());
          method.getThrowsList().add(errorReference);
        }
      }
      final String throwableName = throwException ? "java.lang.CloneNotSupportedException" : "java.lang.AssertionError";
      final PsiStatement statement = factory.createStatementFromText("throw new " + throwableName + "();", element);
      final PsiCodeBlock body = method.getBody();
      assert body != null;
      body.add(statement);
      final PsiExpression superCloneCall = factory.createExpressionFromText("super.clone()", aClass);
      for (PsiMethodCallExpression cloneCall : collectCallsToClone(aClass)) {
        cloneCall.replace(superCloneCall);
      }
      GenerateMembersUtil.positionCaret(updater, method, true);
    }
  }

  private static List<PsiMethodCallExpression> collectCallsToClone(PsiClass aClass) {
    final CloneCallFinder finder = new CloneCallFinder();
    aClass.acceptChildren(finder);
    return finder.getCloneCalls();
  }

  private static class CloneCallFinder extends JavaRecursiveElementWalkingVisitor {

    private final List<PsiMethodCallExpression> cloneCalls = new SmartList<>();

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (CloneUtils.isCallToClone(expression)) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
          return;
        }
        cloneCalls.add(expression);
      }
      else {
        super.visitMethodCallExpression(expression);
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {}

    private List<PsiMethodCallExpression> getCloneCalls() {
      return cloneCalls;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneableClassInSecureContextVisitor();
  }

  private static class CloneableClassInSecureContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isAnnotationType() || aClass instanceof PsiTypeParameter) {
        return;
      }
      if (!CloneUtils.isCloneable(aClass)) {
        return;
      }
      for (final PsiMethod method : aClass.findMethodsByName("clone", true)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
          // optimization
          break;
        }
        if (CloneUtils.isClone(method) && ControlFlowUtils.methodAlwaysThrowsException((PsiMethod)method.getNavigationElement())) {
          return;
        }
      }
      registerClassError(aClass, aClass);
    }
  }
}