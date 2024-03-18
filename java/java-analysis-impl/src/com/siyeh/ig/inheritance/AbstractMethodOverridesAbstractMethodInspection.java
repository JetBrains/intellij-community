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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class AbstractMethodOverridesAbstractMethodInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreJavaDoc = false;

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new AbstractMethodOverridesAbstractMethodFix();
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreJavaDoc", InspectionGadgetsBundle.message(
        "abstract.method.overrides.abstract.method.ignore.different.javadoc.option")));
  }

  private static class AbstractMethodOverridesAbstractMethodFix extends ModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("abstract.method.overrides.abstract.method.remove.quickfix");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodNameIdentifier.getParent();
      assert method != null;
      final PsiMethod[] superMethods = method.findSuperMethods();
      SearchScope scope = GlobalSearchScope.allScope(project);
      final Collection<PsiReference> references = ReferencesSearch.search(method, scope).findAll();
      return ModCommand.psiUpdate(method, (m, updater) -> {
        List<PsiElement> writableRefs = ContainerUtil.map(references, ref -> updater.getWritable(ref.getElement()));
        for (PsiElement e : writableRefs) {
          PsiReference reference = e.getReference();
          if (reference != null) {
            reference.bindToElement(superMethods[0]);
          }
        }
        m.delete();
      });
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AbstractMethodOverridesAbstractMethodVisitor();
  }

  private class AbstractMethodOverridesAbstractMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isConstructor() || !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      boolean overrideDefault = false;
      boolean accept = false;
      final PsiMethod[] superMethods = method.findSuperMethods();
      for (final PsiMethod superMethod : superMethods) {
        overrideDefault |= superMethod.hasModifierProperty(PsiModifier.DEFAULT);
        if (!superMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          continue;
        }
        if (overrideDefault) {
          return;
        }
        accept |= haveSameReturnTypes(method, superMethod) &&
                  haveSameExceptionSignatures(method, superMethod) &&
                  method.isVarArgs() == superMethod.isVarArgs();

        if (ignoreJavaDoc && !haveSameJavaDoc(method, superMethod)) {
          return;
        }
        if (!methodsHaveSameAnnotationsAndModifiers(method, superMethod)) {
          return;
        }
      }
      if (accept && !overrideDefault) {
        registerMethodError(method);
      }
    }
  }

  public static boolean methodsHaveSameAnnotationsAndModifiers(PsiMethod method, PsiMethod superMethod) {
    if (!MethodUtils.haveEquivalentModifierLists(method, superMethod)) {
      return false;
    }
    final PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != superParameters.length) {
      return false;
    }
    for (int i = 0, length = superParameters.length; i < length; i++) {
      if (!haveSameAnnotations(parameters[i], superParameters[i])) {
        return false;
      }
    }
    return true;
  }

  private static boolean haveSameAnnotations(PsiModifierListOwner owner1, PsiModifierListOwner owner2) {
    final PsiModifierList modifierList = owner1.getModifierList();
    final PsiModifierList superModifierList = owner2.getModifierList();
    return (modifierList == null || superModifierList == null)
           ? modifierList == superModifierList
           : AnnotationUtil.equal(modifierList.getAnnotations(), superModifierList.getAnnotations());
  }

  static boolean haveSameJavaDoc(PsiMethod method, PsiMethod superMethod) {
    final PsiDocComment superDocComment = superMethod.getDocComment();
    final PsiDocComment docComment = method.getDocComment();
    if (superDocComment == null) {
      if (docComment != null) {
        return false;
      }
    } else if (docComment != null) {
      if (!superDocComment.getText().equals(docComment.getText())) {
        return false;
      }
    }
    return true;
  }

  public static boolean haveSameExceptionSignatures(PsiMethod method1, PsiMethod method2) {
    final PsiReferenceList list1 = method1.getThrowsList();
    final PsiClassType[] exceptions1 = list1.getReferencedTypes();
    final PsiReferenceList list2 = method2.getThrowsList();
    final PsiClassType[] exceptions2 = list2.getReferencedTypes();
    if (exceptions1.length != exceptions2.length) {
      return false;
    }
    final Set<PsiClassType> set1 = ContainerUtil.newHashSet(exceptions1);
    for (PsiClassType anException : exceptions2) {
      if (!set1.contains(anException)) {
        return false;
      }
    }
    return true;
  }

  public static boolean haveSameReturnTypes(PsiMethod method1, PsiMethod method2) {
    return areTypesEqual(method1.getReturnType(), method2.getReturnType());
  }

  public static boolean haveSameParameterTypes(PsiMethod method1, PsiMethod method2){
    PsiParameter[] parameters1 = method1.getParameterList().getParameters();
    PsiParameter[] parameters2 = method2.getParameterList().getParameters();
    for (int i = 0; i < parameters1.length; i++) {
      if (!areTypesEqual(parameters1[i].getType(), parameters2[i].getType())) return false;
    }
    return true;
  }

  private static boolean areTypesEqual(@Nullable PsiType type, @Nullable PsiType bound){
    if (type == null || bound == null) return false;
    PsiType typeErasure = TypeConversionUtil.erasure(type);
    PsiType boundErasure = TypeConversionUtil.erasure(bound);
    if(!typeErasure.equals(boundErasure)) return false;
    if (!(type instanceof PsiClassType classType && bound instanceof PsiClassType boundClassType)) return true;
    PsiType[] typeParameters = classType.getParameters();
    PsiType[] boundParameters = boundClassType.getParameters();
    if (typeParameters.length != boundParameters.length) return false;
    for (int i = 0; i < typeParameters.length; i++) {
      if (!areTypesEqual(typeParameters[i], boundParameters[i])) return false;
    }
    return true;
  }

  public static @Nullable PsiSubstitutor getSuperSubstitutor(PsiMethod method, PsiMethod superMethod) {
    PsiClass contextClass = method.getContainingClass();
    PsiClass superClass = superMethod.getContainingClass();
    if (contextClass == null || superClass == null) return null;
    PsiSubstitutor classSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, contextClass, PsiSubstitutor.EMPTY);
    MethodSignature contextSignature = method.getSignature(PsiSubstitutor.EMPTY);
    MethodSignature superSignature = superMethod.getSignature(classSubstitutor);
    return MethodSignatureUtil.getSuperMethodSignatureSubstitutor(contextSignature, superSignature);
  }
}